/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.cqrs.query;
import io.ddd4j.kit.lang.SFunction;
import io.ddd4j.kit.lang.LambdaKit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.*;

import java.io.Serializable;
import java.util.*;

/**
 * 充血查询模型（Lambda 类型安全条件构建 + 充血查询执行）。
 *
 * <p>业务方继承此类后，直接通过 Lambda 引用领域模型字段构建查询条件：
 * <pre>{@code
 * public class OrderQuery extends Query<Order> {
 *     // 绑定仓储
 *     protected Repository repository() {
 *         return RepositoryRegistry.repository(Order.class);
 *     }
 * }
 *
 * // 使用
 * Page<Order> page = new OrderQuery()
 *     .paid()
 *     .orderNoContains("2024")
 *     .createdFrom(startTime)
 *     .newestFirst()
 *     .current(1).size(20)
 *     .page();
 *
 * List<Order> list = new OrderQuery()
 *     .eq(Order::getStatus, "ACTIVE")
 *     .list();
 *
 * // 基础设施层需要直接使用 PO 属性时，必须显式进入类型安全作用域
 * Page<Order> persistencePage = new OrderQuery()
 *     .withPO(OrderPO.class)
 *     .eq(OrderPO::getStatus, "PAID")
 *     .orderByDesc(OrderPO::getCreateTime)
 *     .current(1).size(20)
 *     .page();
 * }</pre>
 *
 * <p>每个条件方法都有 {@code boolean condition} 重载，消除 if-else 样板：
 * <pre>{@code
 * query.eq(StrKit.isNotBlank(status), Order::getStatus, status)
 *      .like(StrKit.isNotBlank(keyword), Order::getName, keyword);
 * }</pre>
 *
 * <p>条件存储在 {@link #conditions} 列表中，由各 ORM 模块的 Repository 读取并转换为原生查询。
 *
 * @param <M> 聚合根类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@EqualsAndHashCode
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class Query<M extends AggregateRoot<?>> implements Serializable {

    // ========================= 分页参数 =========================

    /**
     * 当前页码（从 1 开始）
     */
    protected long current = 1L;
    /**
     * 每页大小（-1 表示不分页）
     */
    protected long size = 10L;

    // ========================= 查询控制 =========================

    /**
     * 原生 SQL HAVING（配合 GROUP BY 使用，聚合条件）
     */
    protected String having;
    /**
     * 聚合填充字段（逗号分隔，查询后自动加载关联数据）
     */
    protected String fills;
    /**
     * Lambda 查询条件列表（WHERE 条件）
     */
    @ToString.Exclude
    @JsonIgnore
    protected transient List<LambdaCondition> conditions;
    /**
     * Lambda 排序条件列表
     */
    @ToString.Exclude
    @JsonIgnore
    protected transient List<LambdaCondition> orderByConditions;
    /**
     * Lambda 更新 SET 操作列表
     */
    @ToString.Exclude
    @JsonIgnore
    protected transient List<LambdaCondition> setOperations;
    /**
     * Lambda 查询字段（SELECT）
     */
    @ToString.Exclude
    @JsonIgnore
    protected transient List<String> selectColumns;
    /**
     * Lambda 分组字段（GROUP BY）
     */
    @ToString.Exclude
    @JsonIgnore
    protected transient List<String> groupByColumns;
    /**
     * 是否忽略租户隔离（跨租户管理操作时使用）
     */
    @ToString.Exclude
    @JsonIgnore
    private boolean ignoreTenantId = false;

    // ========================= 条件构建 — 等于/不等于 =========================

    /**
     * \u6dfb\u52a0\u7b49\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q eq(SFunction<M, ?> column, Object value) {
        return eq(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u7b49\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q eq(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "=", value);
    }

    /**
     * \u6dfb\u52a0\u4e0d\u7b49\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q ne(SFunction<M, ?> column, Object value) {
        return ne(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u4e0d\u7b49\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q ne(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<>", value);
    }

    // ========================= 条件构建 — 模糊匹配 =========================

    /**
     * \u6dfb\u52a0\u6a21\u7cca\u5339\u914d\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q like(SFunction<M, ?> column, Object value) {
        return like(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u6a21\u7cca\u5339\u914d\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q like(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE", value);
    }

    /**
     * \u6dfb\u52a0\u5de6\u4fa7\u6a21\u7cca\u5339\u914d\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q likeLeft(SFunction<M, ?> column, Object value) {
        return likeLeft(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u5de6\u4fa7\u6a21\u7cca\u5339\u914d\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q likeLeft(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE_LEFT", value);
    }

    /**
     * \u6dfb\u52a0\u53f3\u4fa7\u6a21\u7cca\u5339\u914d\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q likeRight(SFunction<M, ?> column, Object value) {
        return likeRight(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u53f3\u4fa7\u6a21\u7cca\u5339\u914d\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q likeRight(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE_RIGHT", value);
    }

    /**
     * \u6dfb\u52a0\u975e\u6a21\u7cca\u5339\u914d\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q notLike(SFunction<M, ?> column, Object value) {
        return notLike(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u975e\u6a21\u7cca\u5339\u914d\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q notLike(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "NOT_LIKE", value);
    }

    // ========================= 条件构建 — 大小比较 =========================

    /**
     * \u6dfb\u52a0\u5927\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q gt(SFunction<M, ?> column, Object value) {
        return gt(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u5927\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q gt(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, ">", value);
    }

    /**
     * \u6dfb\u52a0\u5927\u4e8e\u6216\u7b49\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q ge(SFunction<M, ?> column, Object value) {
        return ge(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u5927\u4e8e\u6216\u7b49\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q ge(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, ">=", value);
    }

    /**
     * \u6dfb\u52a0\u5c0f\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q lt(SFunction<M, ?> column, Object value) {
        return lt(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u5c0f\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q lt(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<", value);
    }

    /**
     * \u6dfb\u52a0\u5c0f\u4e8e\u6216\u7b49\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q le(SFunction<M, ?> column, Object value) {
        return le(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u5c0f\u4e8e\u6216\u7b49\u4e8e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q le(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<=", value);
    }

    /**
     * \u8303\u56f4\u67e5\u8be2\uff08BETWEEN \u8bed\u4e49\uff0c\u5c55\u5f00\u4e3a ge + le\uff09\u3002
     *
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param start \u8303\u56f4\u4e0b\u754c\uff0cnull \u65f6\u5ffd\u7565
     * @param end \u8303\u56f4\u4e0a\u754c\uff0cnull \u65f6\u5ffd\u7565
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q between(SFunction<M, ?> column, Object start, Object end) {
        return between(true, column, start, end);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u8303\u56f4\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param start \u8303\u56f4\u4e0b\u754c\uff0cnull \u65f6\u5ffd\u7565
     * @param end \u8303\u56f4\u4e0a\u754c\uff0cnull \u65f6\u5ffd\u7565
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q between(boolean condition, SFunction<M, ?> column, Object start, Object end) {
        if (condition) {
            if (Objects.nonNull(start)) {
                addCondition(true, column, ">=", start);
            }
            if (Objects.nonNull(end)) {
                addCondition(true, column, "<=", end);
            }
        }
        return (Q) this;
    }

    // ========================= 条件构建 — IN/NOT IN =========================

    /**
     * \u6dfb\u52a0\u96c6\u5408\u5305\u542b\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param values \u5019\u9009\u503c\u96c6\u5408\uff1b\u4e3a\u7a7a\u65f6\u5ffd\u7565\u6761\u4ef6
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q in(SFunction<M, ?> column, Collection<?> values) {
        return in(true, column, values);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u96c6\u5408\u5305\u542b\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param values \u5019\u9009\u503c\u96c6\u5408\uff1b\u4e3a\u7a7a\u65f6\u5ffd\u7565\u6761\u4ef6
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q in(boolean condition, SFunction<M, ?> column, Collection<?> values) {
        return addCondition(condition && CollKit.isNotEmpty(values), column, "IN",
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    /**
     * \u6dfb\u52a0\u96c6\u5408\u6392\u9664\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param values \u5019\u9009\u503c\u96c6\u5408\uff1b\u4e3a\u7a7a\u65f6\u5ffd\u7565\u6761\u4ef6
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q notIn(SFunction<M, ?> column, Collection<?> values) {
        return notIn(true, column, values);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u96c6\u5408\u6392\u9664\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param values \u5019\u9009\u503c\u96c6\u5408\uff1b\u4e3a\u7a7a\u65f6\u5ffd\u7565\u6761\u4ef6
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q notIn(boolean condition, SFunction<M, ?> column, Collection<?> values) {
        return addCondition(condition && CollKit.isNotEmpty(values), column, "NOT_IN",
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    // ========================= 条件构建 — NULL 判断 =========================

    /**
     * \u6dfb\u52a0\u4e3a\u7a7a\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q isNull(SFunction<M, ?> column) {
        return isNull(true, column);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u4e3a\u7a7a\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q isNull(boolean condition, SFunction<M, ?> column) {
        return addCondition(condition, column, "IS_NULL", null);
    }

    /**
     * \u6dfb\u52a0\u975e\u7a7a\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q isNotNull(SFunction<M, ?> column) {
        return isNotNull(true, column);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u975e\u7a7a\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q isNotNull(boolean condition, SFunction<M, ?> column) {
        return addCondition(condition, column, "IS_NOT_NULL", null);
    }

    // ========================= 排序 =========================

    /**
     * \u6dfb\u52a0\u5347\u5e8f\u6392\u5e8f\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q orderByAsc(SFunction<M, ?> column) {
        return orderByAsc(true, column);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u5347\u5e8f\u6392\u5e8f\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q orderByAsc(boolean condition, SFunction<M, ?> column) {
        if (condition) {
            addOrderBy(PropertyRef.domain(column), "ASC");
        }
        return (Q) this;
    }

    /**
     * \u6dfb\u52a0\u964d\u5e8f\u6392\u5e8f\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q orderByDesc(SFunction<M, ?> column) {
        return orderByDesc(true, column);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u964d\u5e8f\u6392\u5e8f\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q orderByDesc(boolean condition, SFunction<M, ?> column) {
        if (condition) {
            addOrderBy(PropertyRef.domain(column), "DESC");
        }
        return (Q) this;
    }

    // ========================= 更新 SET 操作 =========================

    /**
     * \u6dfb\u52a0\u66f4\u65b0\u8d4b\u503c\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q set(SFunction<M, ?> column, Object value) {
        return set(true, column, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u66f4\u65b0\u8d4b\u503c\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param column \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c\u6216\u66f4\u65b0\u503c
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q set(boolean condition, SFunction<M, ?> column, Object value) {
        if (condition) {
            addSetOperation(PropertyRef.domain(column), "=", value);
        }
        return (Q) this;
    }

    // ========================= SELECT / GROUP BY（Lambda） =========================

    /**
     * \u6dfb\u52a0\u67e5\u8be2\u5b57\u6bb5\u9009\u62e9\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param columns \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528\u96c6\u5408
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    @SafeVarargs
    public final <Q extends Query<M>> Q select(SFunction<M, ?>... columns) {
        return select(true, columns);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u67e5\u8be2\u5b57\u6bb5\u9009\u62e9\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param columns \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528\u96c6\u5408
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    @SafeVarargs
    public final <Q extends Query<M>> Q select(boolean condition, SFunction<M, ?>... columns) {
        if (condition && Objects.nonNull(columns) && columns.length > 0) {
            if (Objects.isNull(this.selectColumns)) {
                this.selectColumns = new ArrayList<>();
            }
            for (SFunction<M, ?> column : columns) {
                this.selectColumns.add(LambdaKit.resolve(column));
            }
        }
        return (Q) this;
    }

    /**
     * \u6dfb\u52a0\u5206\u7ec4\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param columns \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528\u96c6\u5408
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    @SafeVarargs
    public final <Q extends Query<M>> Q groupBy(SFunction<M, ?>... columns) {
        return groupBy(true, columns);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u5206\u7ec4\u67e5\u8be2\u6761\u4ef6\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param columns \u9886\u57df\u6a21\u578b\u5c5e\u6027\u5f15\u7528\u96c6\u5408
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    @SafeVarargs
    public final <Q extends Query<M>> Q groupBy(boolean condition, SFunction<M, ?>... columns) {
        if (condition && Objects.nonNull(columns) && columns.length > 0) {
            if (Objects.isNull(this.groupByColumns)) {
                this.groupByColumns = new ArrayList<>();
            }
            for (SFunction<M, ?> column : columns) {
                this.groupByColumns.add(LambdaKit.resolve(column));
            }
        }
        return (Q) this;
    }

    /**
     * \u539f\u751f SQL HAVING\uff08\u76f4\u63a5\u900f\u4f20\u7ed9 ORM\uff0c\u914d\u5408 GROUP BY \u4f7f\u7528\uff09\u3002
     *
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param having \u539f\u751f SQL HAVING \u6761\u4ef6
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q having(String having) {
        this.setHaving(having);
        return (Q) this;
    }

    // ========================= 分页 / 隔离控制 =========================

    /**
     * \u8bbe\u7f6e\u5f53\u524d\u9875\u7801\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param current \u5f53\u524d\u9875\u7801\uff0c\u4ece 1 \u5f00\u59cb
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q current(long current) {
        this.setCurrent(current);
        return (Q) this;
    }

    /**
     * \u8bbe\u7f6e\u6bcf\u9875\u6761\u6570\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param size \u6bcf\u9875\u6761\u6570\uff0c-1 \u8868\u793a\u4e0d\u5206\u9875
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q size(long size) {
        this.setSize(size);
        return (Q) this;
    }

    /**
     * \u8bbe\u7f6e\u6bcf\u9875\u6761\u6570\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param size \u6bcf\u9875\u6761\u6570\uff0c-1 \u8868\u793a\u4e0d\u5206\u9875
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q size(boolean condition, long size) {
        if (condition) {
            this.setSize(size);
        }
        return (Q) this;
    }

    /**
     * \u5ffd\u7565\u79df\u6237\u9694\u79bb\uff0c\u4ec5\u7528\u4e8e\u83b7\u6388\u6743\u7684\u8de8\u79df\u6237\u7ba1\u7406\u67e5\u8be2\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q ignoreTenantId() {
        this.ignoreTenantId = true;
        return (Q) this;
    }

    /**
     * \u5173\u95ed\u5206\u9875\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q ignorePage() {
        this.setSize(-1);
        return (Q) this;
    }

    /**
     * 进入显式 PO 属性作用域。
     *
     * <p>这是基础设施层的受控逃生口。默认查询仍应使用领域语义方法或领域属性；
     * PO 类型必须显式传入，以保证 Java 链式调用能够正确推断属性方法引用类型。
     *
     * @param persistenceType 当前 Repository 使用的持久化对象类型
     * @param <P>             持久化对象类型
     * @return PO 查询作用域
     */
    public <P> PersistenceQueryScope<M, P> withPO(Class<P> persistenceType) {
        return new PersistenceQueryScope<>(this, persistenceType);
    }

    // ========================= 查询前钩子 =========================

    /** \u67e5\u8be2\u6267\u884c\u524d\u94a9\u5b50\uff0c\u9ed8\u8ba4\u4e0d\u6267\u884c\u64cd\u4f5c\uff1b\u5b50\u7c7b\u53ef\u8986\u5199\u3002 */
    public void with() {
    }

    // ========================= 聚合填充 =========================

    /**
     * \u8ffd\u52a0\u9700\u8981\u586b\u5145\u7684\u5173\u8054\u5b57\u6bb5\u3002
     * @param <Q> \u5177\u4f53\u67e5\u8be2\u5b50\u7c7b\u578b
     * @param fills \u9700\u8981\u586b\u5145\u7684\u5173\u8054\u5b57\u6bb5\u540d
     * @return \u5f53\u524d\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public <Q extends Query<M>> Q fills(String... fills) {
        if (Objects.nonNull(fills) && fills.length > 0) {
            if (Objects.nonNull(this.fills)) {
                this.setFills(this.fills + "," + String.join(",", fills));
            } else {
                this.setFills(String.join(",", fills));
            }
        }
        return (Q) this;
    }

    /**
     * \u5224\u65ad\u662f\u5426\u8bf7\u6c42\u586b\u5145\u6307\u5b9a\u5173\u8054\u5b57\u6bb5\u3002
     * @param fill \u5f85\u68c0\u67e5\u7684\u5173\u8054\u5b57\u6bb5\u540d
     * @return \u5305\u542b\u8be5\u5173\u8054\u5b57\u6bb5\u65f6\u4e3a true
     */
    public Boolean fill(String fill) {
        if (StrKit.isEmpty(this.fills)) {
            return false;
        }
        return Arrays.asList(this.fills.split(",")).contains(fill);
    }

    /**
     * \u59d4\u6258\u4ed3\u50a8\u586b\u5145\u67e5\u8be2\u7ed3\u679c\u4e2d\u7684\u5173\u8054\u5b57\u6bb5\u3002
     * @param models \u5f85\u586b\u5145\u7684\u805a\u5408\u6839\u5217\u8868
     */
    public void doFills(List<? extends AggregateRoot<?>> models) {
        if (CollKit.isEmpty(models)) {
            return;
        }
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            ((Repository) repo).fill(this, models);
        }
    }

    // ========================= 条件访问器（供 Repository 读取） =========================

    /**
     * \u5224\u65ad\u662f\u5426\u5df2\u6709 WHERE \u6761\u4ef6\u3002
     * @return \u5b58\u5728 WHERE \u6761\u4ef6\u65f6\u4e3a true
     */
    @JsonIgnore
    public boolean hasConditions() {
        return CollKit.isNotEmpty(conditions);
    }

    /**
     * \u5224\u65ad\u662f\u5426\u5df2\u6709\u6392\u5e8f\u6761\u4ef6\u3002
     * @return \u5b58\u5728\u6392\u5e8f\u6761\u4ef6\u65f6\u4e3a true
     */
    @JsonIgnore
    public boolean hasOrderBy() {
        return CollKit.isNotEmpty(orderByConditions);
    }

    /**
     * \u5224\u65ad\u662f\u5426\u5df2\u6709\u66f4\u65b0\u8d4b\u503c\u64cd\u4f5c\u3002
     * @return \u5b58\u5728\u66f4\u65b0\u64cd\u4f5c\u65f6\u4e3a true
     */
    @JsonIgnore
    public boolean hasSet() {
        return CollKit.isNotEmpty(setOperations);
    }

    /**
     * \u5224\u65ad\u662f\u5426\u5df2\u6307\u5b9a\u67e5\u8be2\u5b57\u6bb5\u3002
     * @return \u5b58\u5728\u67e5\u8be2\u5b57\u6bb5\u65f6\u4e3a true
     */
    @JsonIgnore
    public boolean hasSelect() {
        return CollKit.isNotEmpty(selectColumns);
    }

    /**
     * \u5224\u65ad\u662f\u5426\u5df2\u6307\u5b9a\u5206\u7ec4\u5b57\u6bb5\u3002
     * @return \u5b58\u5728\u5206\u7ec4\u5b57\u6bb5\u65f6\u4e3a true
     */
    @JsonIgnore
    public boolean hasGroupBy() {
        return CollKit.isNotEmpty(groupByColumns);
    }

    /**
     * \u83b7\u53d6 WHERE \u6761\u4ef6\u5217\u8868\uff1b\u672a\u8bbe\u7f6e\u65f6\u8fd4\u56de\u7a7a\u5217\u8868\u3002
     * @return WHERE \u6761\u4ef6\u5217\u8868
     */
    @JsonIgnore
    public List<LambdaCondition> getWhereConditions() {
        return Objects.nonNull(conditions) ? conditions : Collections.emptyList();
    }

    /**
     * \u83b7\u53d6\u6392\u5e8f\u6761\u4ef6\u5217\u8868\uff1b\u672a\u8bbe\u7f6e\u65f6\u8fd4\u56de\u7a7a\u5217\u8868\u3002
     * @return \u6392\u5e8f\u6761\u4ef6\u5217\u8868
     */
    @JsonIgnore
    public List<LambdaCondition> getOrderByConditions() {
        return Objects.nonNull(orderByConditions) ? orderByConditions : Collections.emptyList();
    }

    /**
     * \u83b7\u53d6\u66f4\u65b0\u8d4b\u503c\u64cd\u4f5c\u5217\u8868\uff1b\u672a\u8bbe\u7f6e\u65f6\u8fd4\u56de\u7a7a\u5217\u8868\u3002
     * @return \u66f4\u65b0\u8d4b\u503c\u64cd\u4f5c\u5217\u8868
     */
    @JsonIgnore
    public List<LambdaCondition> getSetOperations() {
        return Objects.nonNull(setOperations) ? setOperations : Collections.emptyList();
    }

    /**
     * \u83b7\u53d6\u67e5\u8be2\u5b57\u6bb5\u5217\u8868\uff1b\u672a\u8bbe\u7f6e\u65f6\u8fd4\u56de\u7a7a\u5217\u8868\u3002
     * @return \u67e5\u8be2\u5b57\u6bb5\u5217\u8868
     */
    @JsonIgnore
    public List<String> getSelectColumns() {
        return Objects.nonNull(selectColumns) ? selectColumns : Collections.emptyList();
    }

    /**
     * \u83b7\u53d6\u5206\u7ec4\u5b57\u6bb5\u5217\u8868\uff1b\u672a\u8bbe\u7f6e\u65f6\u8fd4\u56de\u7a7a\u5217\u8868\u3002
     * @return \u5206\u7ec4\u5b57\u6bb5\u5217\u8868
     */
    @JsonIgnore
    public List<String> getGroupByColumns() {
        return Objects.nonNull(groupByColumns) ? groupByColumns : Collections.emptyList();
    }

    // ========================= 分页计算 =========================

    /**
     * \u8ba1\u7b97\u5206\u9875\u8d77\u59cb\u504f\u79fb\u3002
     * @return \u5206\u9875\u8d77\u59cb\u504f\u79fb
     */
    @JsonIgnore
    public long getStartIndex() {
        return (getCurrent() - 1L) * getSize();
    }

    /**
     * \u8ba1\u7b97\u5206\u9875\u7ed3\u675f\u504f\u79fb\u3002
     * @return \u5206\u9875\u7ed3\u675f\u504f\u79fb
     */
    @JsonIgnore
    public long getEndIndex() {
        return getStartIndex() + getSize();
    }

    // ========================= 充血查询方法 =========================

    /**
     * \u6267\u884c\u5217\u8868\u67e5\u8be2\u3002
     * @return \u5339\u914d\u7684\u805a\u5408\u6839\u5217\u8868
     */
    public List<M> list() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).findList(this);
        }
        throw new BizRuntimeException("Repository for {} does not support list()", this.getClass().getSimpleName());
    }

    /**
     * \u6267\u884c\u5217\u8868\u67e5\u8be2\uff1b\u65e0\u7ed3\u679c\u65f6\u629b\u51fa\u4e1a\u52a1\u5f02\u5e38\u3002
     * @param ifEmpty \u67e5\u8be2\u4e3a\u7a7a\u65f6\u7684\u9519\u8bef\u6d88\u606f
     * @param params \u7528\u4e8e\u683c\u5f0f\u5316\u9519\u8bef\u6d88\u606f\u7684\u53c2\u6570
     * @return \u5339\u914d\u7684\u805a\u5408\u6839\u5217\u8868
     */
    public List<M> list(String ifEmpty, Object... params) {
        List<M> list = list();
        if (CollKit.isEmpty(list)) {
            throw new BizRuntimeException(ifEmpty, params);
        }
        return list;
    }

    /**
     * \u6267\u884c\u5206\u9875\u67e5\u8be2\u3002
     * @return \u5206\u9875\u67e5\u8be2\u7ed3\u679c
     */
    public Page<M> page() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).page(this);
        }
        throw new BizRuntimeException("Repository for {} does not support page()", this.getClass().getSimpleName());
    }

    /**
     * \u6267\u884c\u5206\u9875\u67e5\u8be2\uff1b\u65e0\u7ed3\u679c\u65f6\u629b\u51fa\u4e1a\u52a1\u5f02\u5e38\u3002
     * @param ifEmpty \u67e5\u8be2\u4e3a\u7a7a\u65f6\u7684\u9519\u8bef\u6d88\u606f
     * @param params \u7528\u4e8e\u683c\u5f0f\u5316\u9519\u8bef\u6d88\u606f\u7684\u53c2\u6570
     * @return \u5206\u9875\u67e5\u8be2\u7ed3\u679c
     */
    public Page<M> page(String ifEmpty, Object... params) {
        Page<M> p = page();
        if (Objects.isNull(p) || p.isEmpty()) {
            throw new BizRuntimeException(ifEmpty, params);
        }
        return p;
    }

    /**
     * \u67e5\u8be2\u7b2c\u4e00\u6761\u5339\u914d\u8bb0\u5f55\u3002
     * @return \u7b2c\u4e00\u6761\u805a\u5408\u6839\uff0c\u672a\u5339\u914d\u65f6\u4e3a null
     */
    public M one() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return (M) ((Repository) repo).findFirst(this).orElse(null);
        }
        throw new BizRuntimeException("Repository for {} does not support one()", this.getClass().getSimpleName());
    }

    /**
     * \u4ee5 Optional \u8fd4\u56de\u7b2c\u4e00\u6761\u5339\u914d\u8bb0\u5f55\u3002
     * @return \u7b2c\u4e00\u6761\u805a\u5408\u6839\u7684 Optional
     */
    public Optional<M> oneOpt() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).findFirst(this);
        }
        throw new BizRuntimeException("Repository for {} does not support oneOpt()", this.getClass().getSimpleName());
    }

    /**
     * \u4ee5 Optional \u8fd4\u56de\u7b2c\u4e00\u6761\u5339\u914d\u8bb0\u5f55\u3002
     * @return \u7b2c\u4e00\u6761\u805a\u5408\u6839\u7684 Optional
     */
    public Optional<M> firstOpt() {
        return oneOpt();
    }

    /**
     * \u67e5\u8be2\u7b2c\u4e00\u6761\u5339\u914d\u8bb0\u5f55\uff1b\u65e0\u7ed3\u679c\u65f6\u629b\u51fa\u4e1a\u52a1\u5f02\u5e38\u3002
     * @param ifNull \u67e5\u8be2\u65e0\u7ed3\u679c\u65f6\u7684\u9519\u8bef\u6d88\u606f
     * @param params \u7528\u4e8e\u683c\u5f0f\u5316\u9519\u8bef\u6d88\u606f\u7684\u53c2\u6570
     * @return \u7b2c\u4e00\u6761\u805a\u5408\u6839\uff0c\u672a\u5339\u914d\u65f6\u4e3a null
     */
    public M one(String ifNull, Object... params) {
        M one = one();
        if (Objects.isNull(one)) {
            throw new BizRuntimeException(ifNull, params);
        }
        return one;
    }

    /**
     * \u67e5\u8be2\u7b2c\u4e00\u6761\u5339\u914d\u8bb0\u5f55\u3002
     * @return \u7b2c\u4e00\u6761\u805a\u5408\u6839\uff0c\u672a\u5339\u914d\u65f6\u4e3a null
     */
    public M first() {
        return one();
    }

    /**
     * \u67e5\u8be2\u7b2c\u4e00\u6761\u5339\u914d\u8bb0\u5f55\uff1b\u65e0\u7ed3\u679c\u65f6\u629b\u51fa\u4e1a\u52a1\u5f02\u5e38\u3002
     * @param ifNull \u67e5\u8be2\u65e0\u7ed3\u679c\u65f6\u7684\u9519\u8bef\u6d88\u606f
     * @param params \u7528\u4e8e\u683c\u5f0f\u5316\u9519\u8bef\u6d88\u606f\u7684\u53c2\u6570
     * @return \u7b2c\u4e00\u6761\u805a\u5408\u6839\uff0c\u672a\u5339\u914d\u65f6\u4e3a null
     */
    public M first(String ifNull, Object... params) {
        return one(ifNull, params);
    }

    /**
     * \u7edf\u8ba1\u5339\u914d\u8bb0\u5f55\u6570\u3002
     * @return \u5339\u914d\u8bb0\u5f55\u6570
     */
    public long count() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).count(this);
        }
        throw new BizRuntimeException("Repository for {} does not support count()", this.getClass().getSimpleName());
    }

    /**
     * \u5224\u65ad\u662f\u5426\u5b58\u5728\u5339\u914d\u8bb0\u5f55\u3002
     * @return \u5b58\u5728\u5339\u914d\u8bb0\u5f55\u65f6\u4e3a true
     */
    public boolean exists() {
        return count() > 0;
    }

    /**
     * \u5224\u65ad\u662f\u5426\u5b58\u5728\u5339\u914d\u8bb0\u5f55\u3002
     * @return \u5b58\u5728\u5339\u914d\u8bb0\u5f55\u65f6\u4e3a true
     */
    public boolean exist() {
        return exists();
    }

    /**
     * \u65ad\u8a00\u5b58\u5728\u5339\u914d\u8bb0\u5f55\uff1b\u65ad\u8a00\u5931\u8d25\u65f6\u629b\u51fa\u4e1a\u52a1\u5f02\u5e38\u3002
     * @param ifNotExist \u4e0d\u5b58\u5728\u5339\u914d\u8bb0\u5f55\u65f6\u7684\u9519\u8bef\u6d88\u606f
     * @param params \u7528\u4e8e\u683c\u5f0f\u5316\u9519\u8bef\u6d88\u606f\u7684\u53c2\u6570
     */
    public void exist(String ifNotExist, Object... params) {
        if (!exist()) {
            throw new BizRuntimeException(ifNotExist, params);
        }
    }

    /**
     * \u5224\u65ad\u662f\u5426\u4e0d\u5b58\u5728\u5339\u914d\u8bb0\u5f55\u3002
     * @return \u4e0d\u5b58\u5728\u5339\u914d\u8bb0\u5f55\u65f6\u4e3a true
     */
    public boolean notExist() {
        return !exist();
    }

    /**
     * \u65ad\u8a00\u4e0d\u5b58\u5728\u5339\u914d\u8bb0\u5f55\uff1b\u65ad\u8a00\u5931\u8d25\u65f6\u629b\u51fa\u4e1a\u52a1\u5f02\u5e38\u3002
     * @param ifExist \u5b58\u5728\u5339\u914d\u8bb0\u5f55\u65f6\u7684\u9519\u8bef\u6d88\u606f
     * @param params \u7528\u4e8e\u683c\u5f0f\u5316\u9519\u8bef\u6d88\u606f\u7684\u53c2\u6570
     */
    public void notExist(String ifExist, Object... params) {
        if (exist()) {
            throw new BizRuntimeException(ifExist, params);
        }
    }

    // ========================= Map 查询 =========================

    /**
     * \u6267\u884c Map \u5217\u8868\u67e5\u8be2\u3002
     * @return Map \u67e5\u8be2\u7ed3\u679c\u5217\u8868
     */
    public List<Map<String, Object>> maps() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return repo.maps((Query) this);
        }
        throw new BizRuntimeException("Repository for {} does not support maps()", this.getClass().getSimpleName());
    }

    /**
     * \u83b7\u53d6\u7b2c\u4e00\u6761 Map \u67e5\u8be2\u7ed3\u679c\u3002
     * @return \u7b2c\u4e00\u6761 Map \u7ed3\u679c\uff0c\u672a\u5339\u914d\u65f6\u4e3a\u7a7a Map
     */
    public Map<String, Object> map() {
        List<Map<String, Object>> result = maps();
        if (Objects.isNull(result) || result.isEmpty()) {
            return Collections.emptyMap();
        }
        return result.get(0);
    }

    // ========================= 仓储查找 =========================

    /**
     * \u4ece\u6ce8\u518c\u8868\u67e5\u627e\u6b64\u67e5\u8be2\u7c7b\u578b\u7684\u4ed3\u50a8\u3002
     * @return \u6b64\u67e5\u8be2\u7c7b\u578b\u7684\u4ed3\u50a8
     */
    public Repository repository() {
        return RepositoryRegistry.repositoryForQuery(this.getClass());
    }

    // ========================= 内部方法 =========================

    /**
     * \u5728\u6761\u4ef6\u5f00\u542f\u4e14\u64cd\u4f5c\u6570\u7b26\u5408\u8fd0\u7b97\u7b26\u8981\u6c42\u65f6\u5199\u5165\u7b5b\u9009\u6761\u4ef6\u3002
     * @param <Q> \u5f53\u524d\u67e5\u8be2\u7684\u8fd4\u56de\u7c7b\u578b
     * @param condition \u662f\u5426\u8ffd\u52a0\u7b5b\u9009\u6761\u4ef6
     * @param column \u8981\u7b5b\u9009\u7684\u5c5e\u6027\u5f15\u7528\u6216\u9886\u57df\u8bbf\u95ee\u5668
     * @param operator \u6761\u4ef6\u8fd0\u7b97\u7b26
     * @param value \u7528\u4e8e\u6bd4\u8f83\u7684\u7b5b\u9009\u503c
     * @return \u652f\u6301\u94fe\u5f0f\u8c03\u7528\u7684\u5f53\u524d\u67e5\u8be2
     */
    private <Q extends Query<M>> Q addCondition(boolean condition, SFunction<M, ?> column, String operator, Object value) {
        return addCondition(condition, PropertyRef.domain(column), operator, value);
    }

    /**
     * \u5728\u6761\u4ef6\u5f00\u542f\u4e14\u64cd\u4f5c\u6570\u7b26\u5408\u8fd0\u7b97\u7b26\u8981\u6c42\u65f6\u5199\u5165\u7b5b\u9009\u6761\u4ef6\u3002
     * @param <Q> \u5f53\u524d\u67e5\u8be2\u7684\u8fd4\u56de\u7c7b\u578b
     * @param condition \u662f\u5426\u8ffd\u52a0\u7b5b\u9009\u6761\u4ef6
     * @param propertyRef \u8981\u7b5b\u9009\u7684\u5c5e\u6027\u5f15\u7528\u6216\u9886\u57df\u8bbf\u95ee\u5668
     * @param operator \u6761\u4ef6\u8fd0\u7b97\u7b26
     * @param value \u7528\u4e8e\u6bd4\u8f83\u7684\u7b5b\u9009\u503c
     * @return \u652f\u6301\u94fe\u5f0f\u8c03\u7528\u7684\u5f53\u524d\u67e5\u8be2
     */
    <Q extends Query<M>> Q addCondition(boolean condition, PropertyRef propertyRef, String operator, Object value) {
        boolean valueOptional = Objects.equals("IS_NULL", operator) || Objects.equals("IS_NOT_NULL", operator);
        if (condition && (Objects.nonNull(value) || valueOptional)) {
            if (Objects.isNull(this.conditions)) {
                this.conditions = new ArrayList<>();
            }
            this.conditions.add(new LambdaCondition(propertyRef, operator, value));
        }
        return (Q) this;
    }

    /**
     * \u8ffd\u52a0\u4e00\u9879\u6309\u6307\u5b9a\u5c5e\u6027\u6392\u5e8f\u7684\u914d\u7f6e\u3002
     * @param property \u6392\u5e8f\u4f7f\u7528\u7684\u5c5e\u6027\u5f15\u7528
     * @param direction \u5347\u5e8f\u6216\u964d\u5e8f\u7684\u6392\u5e8f\u65b9\u5411
     */
    void addOrderBy(PropertyRef property, String direction) {
        if (Objects.isNull(this.orderByConditions)) {
            this.orderByConditions = new ArrayList<>();
        }
        this.orderByConditions.add(new LambdaCondition(property, direction, null));
    }

    /**
     * \u8ffd\u52a0\u7528\u4e8e\u66f4\u65b0\u6301\u4e45\u5316\u6570\u636e\u7684\u8d4b\u503c\u64cd\u4f5c\u3002
     * @param property \u8981\u66f4\u65b0\u7684\u5c5e\u6027
     * @param operator \u8d4b\u503c\u64cd\u4f5c\u7b26
     * @param value \u5f85\u5199\u5165\u7684\u503c
     */
    private void addSetOperation(PropertyRef property, String operator, Object value) {
        if (Objects.isNull(this.setOperations)) {
            this.setOperations = new ArrayList<>();
        }
        this.setOperations.add(new LambdaCondition(property, operator, value));
    }

}
