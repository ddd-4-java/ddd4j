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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.util.LambdaKit;
import io.ddd4j.core.util.SFunction;
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

    public <Q extends Query<M>> Q eq(SFunction<M, ?> column, Object value) {
        return eq(true, column, value);
    }

    public <Q extends Query<M>> Q eq(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "=", value);
    }

    public <Q extends Query<M>> Q ne(SFunction<M, ?> column, Object value) {
        return ne(true, column, value);
    }

    public <Q extends Query<M>> Q ne(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<>", value);
    }

    // ========================= 条件构建 — 模糊匹配 =========================

    public <Q extends Query<M>> Q like(SFunction<M, ?> column, Object value) {
        return like(true, column, value);
    }

    public <Q extends Query<M>> Q like(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE", value);
    }

    public <Q extends Query<M>> Q likeLeft(SFunction<M, ?> column, Object value) {
        return likeLeft(true, column, value);
    }

    public <Q extends Query<M>> Q likeLeft(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE_LEFT", value);
    }

    public <Q extends Query<M>> Q likeRight(SFunction<M, ?> column, Object value) {
        return likeRight(true, column, value);
    }

    public <Q extends Query<M>> Q likeRight(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE_RIGHT", value);
    }

    public <Q extends Query<M>> Q notLike(SFunction<M, ?> column, Object value) {
        return notLike(true, column, value);
    }

    public <Q extends Query<M>> Q notLike(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "NOT_LIKE", value);
    }

    // ========================= 条件构建 — 大小比较 =========================

    public <Q extends Query<M>> Q gt(SFunction<M, ?> column, Object value) {
        return gt(true, column, value);
    }

    public <Q extends Query<M>> Q gt(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, ">", value);
    }

    public <Q extends Query<M>> Q ge(SFunction<M, ?> column, Object value) {
        return ge(true, column, value);
    }

    public <Q extends Query<M>> Q ge(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, ">=", value);
    }

    public <Q extends Query<M>> Q lt(SFunction<M, ?> column, Object value) {
        return lt(true, column, value);
    }

    public <Q extends Query<M>> Q lt(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<", value);
    }

    public <Q extends Query<M>> Q le(SFunction<M, ?> column, Object value) {
        return le(true, column, value);
    }

    public <Q extends Query<M>> Q le(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<=", value);
    }

    /**
     * 范围查询（BETWEEN 语义，展开为 ge + le）。
     */
    public <Q extends Query<M>> Q between(SFunction<M, ?> column, Object start, Object end) {
        return between(true, column, start, end);
    }

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

    public <Q extends Query<M>> Q in(SFunction<M, ?> column, Collection<?> values) {
        return in(true, column, values);
    }

    public <Q extends Query<M>> Q in(boolean condition, SFunction<M, ?> column, Collection<?> values) {
        return addCondition(condition && CollKit.isNotEmpty(values), column, "IN",
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    public <Q extends Query<M>> Q notIn(SFunction<M, ?> column, Collection<?> values) {
        return notIn(true, column, values);
    }

    public <Q extends Query<M>> Q notIn(boolean condition, SFunction<M, ?> column, Collection<?> values) {
        return addCondition(condition && CollKit.isNotEmpty(values), column, "NOT_IN",
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    // ========================= 条件构建 — NULL 判断 =========================

    public <Q extends Query<M>> Q isNull(SFunction<M, ?> column) {
        return isNull(true, column);
    }

    public <Q extends Query<M>> Q isNull(boolean condition, SFunction<M, ?> column) {
        return addCondition(condition, column, "IS_NULL", null);
    }

    public <Q extends Query<M>> Q isNotNull(SFunction<M, ?> column) {
        return isNotNull(true, column);
    }

    public <Q extends Query<M>> Q isNotNull(boolean condition, SFunction<M, ?> column) {
        return addCondition(condition, column, "IS_NOT_NULL", null);
    }

    // ========================= 排序 =========================

    public <Q extends Query<M>> Q orderByAsc(SFunction<M, ?> column) {
        return orderByAsc(true, column);
    }

    public <Q extends Query<M>> Q orderByAsc(boolean condition, SFunction<M, ?> column) {
        if (condition) {
            addOrderBy(PropertyRef.domain(column), "ASC");
        }
        return (Q) this;
    }

    public <Q extends Query<M>> Q orderByDesc(SFunction<M, ?> column) {
        return orderByDesc(true, column);
    }

    public <Q extends Query<M>> Q orderByDesc(boolean condition, SFunction<M, ?> column) {
        if (condition) {
            addOrderBy(PropertyRef.domain(column), "DESC");
        }
        return (Q) this;
    }

    // ========================= 更新 SET 操作 =========================

    public <Q extends Query<M>> Q set(SFunction<M, ?> column, Object value) {
        return set(true, column, value);
    }

    public <Q extends Query<M>> Q set(boolean condition, SFunction<M, ?> column, Object value) {
        if (condition) {
            addSetOperation(PropertyRef.domain(column), "=", value);
        }
        return (Q) this;
    }

    // ========================= SELECT / GROUP BY（Lambda） =========================

    @SafeVarargs
    public final <Q extends Query<M>> Q select(SFunction<M, ?>... columns) {
        return select(true, columns);
    }

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

    @SafeVarargs
    public final <Q extends Query<M>> Q groupBy(SFunction<M, ?>... columns) {
        return groupBy(true, columns);
    }

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
     * 原生 SQL HAVING（直接透传给 ORM，配合 GROUP BY 使用）。
     */
    public <Q extends Query<M>> Q having(String having) {
        this.setHaving(having);
        return (Q) this;
    }

    // ========================= 分页 / 隔离控制 =========================

    public <Q extends Query<M>> Q current(long current) {
        this.setCurrent(current);
        return (Q) this;
    }

    public <Q extends Query<M>> Q size(long size) {
        this.setSize(size);
        return (Q) this;
    }

    public <Q extends Query<M>> Q size(boolean condition, long size) {
        if (condition) {
            this.setSize(size);
        }
        return (Q) this;
    }

    public <Q extends Query<M>> Q ignoreTenantId() {
        this.ignoreTenantId = true;
        return (Q) this;
    }

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

    public void with() {
    }

    // ========================= 聚合填充 =========================

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

    public Boolean fill(String fill) {
        if (StrKit.isEmpty(this.fills)) {
            return false;
        }
        return Arrays.asList(this.fills.split(",")).contains(fill);
    }

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

    @JsonIgnore
    public boolean hasConditions() {
        return CollKit.isNotEmpty(conditions);
    }

    @JsonIgnore
    public boolean hasOrderBy() {
        return CollKit.isNotEmpty(orderByConditions);
    }

    @JsonIgnore
    public boolean hasSet() {
        return CollKit.isNotEmpty(setOperations);
    }

    @JsonIgnore
    public boolean hasSelect() {
        return CollKit.isNotEmpty(selectColumns);
    }

    @JsonIgnore
    public boolean hasGroupBy() {
        return CollKit.isNotEmpty(groupByColumns);
    }

    @JsonIgnore
    public List<LambdaCondition> getWhereConditions() {
        return Objects.nonNull(conditions) ? conditions : Collections.emptyList();
    }

    @JsonIgnore
    public List<LambdaCondition> getOrderByConditions() {
        return Objects.nonNull(orderByConditions) ? orderByConditions : Collections.emptyList();
    }

    @JsonIgnore
    public List<LambdaCondition> getSetOperations() {
        return Objects.nonNull(setOperations) ? setOperations : Collections.emptyList();
    }

    @JsonIgnore
    public List<String> getSelectColumns() {
        return Objects.nonNull(selectColumns) ? selectColumns : Collections.emptyList();
    }

    @JsonIgnore
    public List<String> getGroupByColumns() {
        return Objects.nonNull(groupByColumns) ? groupByColumns : Collections.emptyList();
    }

    // ========================= 分页计算 =========================

    @JsonIgnore
    public long getStartIndex() {
        return (getCurrent() - 1L) * getSize();
    }

    @JsonIgnore
    public long getEndIndex() {
        return getStartIndex() + getSize();
    }

    // ========================= 充血查询方法 =========================

    public List<M> list() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).findList(this);
        }
        throw new BizRuntimeException("Repository for {} does not support list()", this.getClass().getSimpleName());
    }

    public List<M> list(String ifEmpty, Object... params) {
        List<M> list = list();
        if (CollKit.isEmpty(list)) {
            throw new BizRuntimeException(ifEmpty, params);
        }
        return list;
    }

    public Page<M> page() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).page(this);
        }
        throw new BizRuntimeException("Repository for {} does not support page()", this.getClass().getSimpleName());
    }

    public Page<M> page(String ifEmpty, Object... params) {
        Page<M> p = page();
        if (Objects.isNull(p) || p.isEmpty()) {
            throw new BizRuntimeException(ifEmpty, params);
        }
        return p;
    }

    public M one() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return (M) ((Repository) repo).findFirst(this).orElse(null);
        }
        throw new BizRuntimeException("Repository for {} does not support one()", this.getClass().getSimpleName());
    }

    public Optional<M> oneOpt() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).findFirst(this);
        }
        throw new BizRuntimeException("Repository for {} does not support oneOpt()", this.getClass().getSimpleName());
    }

    public Optional<M> firstOpt() {
        return oneOpt();
    }

    public M one(String ifNull, Object... params) {
        M one = one();
        if (Objects.isNull(one)) {
            throw new BizRuntimeException(ifNull, params);
        }
        return one;
    }

    public M first() {
        return one();
    }

    public M first(String ifNull, Object... params) {
        return one(ifNull, params);
    }

    public long count() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).count(this);
        }
        throw new BizRuntimeException("Repository for {} does not support count()", this.getClass().getSimpleName());
    }

    public boolean exists() {
        return count() > 0;
    }

    public boolean exist() {
        return exists();
    }

    public void exist(String ifNotExist, Object... params) {
        if (!exist()) {
            throw new BizRuntimeException(ifNotExist, params);
        }
    }

    public boolean notExist() {
        return !exist();
    }

    public void notExist(String ifExist, Object... params) {
        if (exist()) {
            throw new BizRuntimeException(ifExist, params);
        }
    }

    // ========================= Map 查询 =========================

    public List<Map<String, Object>> maps() {
        this.with();
        Repository<?, ?> repo = repository();
        if (Objects.nonNull(repo)) {
            return repo.maps((Query) this);
        }
        throw new BizRuntimeException("Repository for {} does not support maps()", this.getClass().getSimpleName());
    }

    public Map<String, Object> map() {
        List<Map<String, Object>> result = maps();
        if (Objects.isNull(result) || result.isEmpty()) {
            return Collections.emptyMap();
        }
        return result.get(0);
    }

    // ========================= 仓储查找 =========================

    public Repository repository() {
        return RepositoryRegistry.repositoryForQuery(this.getClass());
    }

    // ========================= 内部方法 =========================

    private <Q extends Query<M>> Q addCondition(boolean condition, SFunction<M, ?> column, String operator, Object value) {
        return addCondition(condition, PropertyRef.domain(column), operator, value);
    }

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

    void addOrderBy(PropertyRef property, String direction) {
        if (Objects.isNull(this.orderByConditions)) {
            this.orderByConditions = new ArrayList<>();
        }
        this.orderByConditions.add(new LambdaCondition(property, direction, null));
    }

    private void addSetOperation(PropertyRef property, String operator, Object value) {
        if (Objects.isNull(this.setOperations)) {
            this.setOperations = new ArrayList<>();
        }
        this.setOperations.add(new LambdaCondition(property, operator, value));
    }

}
