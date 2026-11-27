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
package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;

import java.io.Serializable;
import java.util.*;

/**
 * 统一的领域仓储接口（对齐 MyBatis-Plus {@code BaseMapper} 全部常用方法）。
 *
 * <p>合并了旧 {@code Repository}（基础 CRUD）和 {@code RichRepository}（充血查询），
 * 因为 Lambda 改造后 {@link Query} 已绑定 PO 类型，无需区分两个接口。
 *
 * <h2>方法对照 BaseMapper</h2>
 * <table border="1">
 *   <caption>BaseMapper 与 Repository 方法对照</caption>
 *   <tr><th>BaseMapper</th><th>Repository</th><th>说明</th></tr>
 *   <tr><td colspan="3"><b>单条 CRUD</b></td></tr>
 *   <tr><td>selectById(id)</td><td>findById(ID)</td><td>按 ID 查询</td></tr>
 *   <tr><td>insert(T)</td><td>save(M)</td><td>插入</td></tr>
 *   <tr><td>updateById(T)</td><td>updateById(M)</td><td>按 ID 更新</td></tr>
 *   <tr><td>insertOrUpdate(T)</td><td>insertOrUpdate(M)</td><td>存在更新否插入</td></tr>
 *   <tr><td>deleteById(id)</td><td>deleteById(ID)</td><td>按 ID 删除</td></tr>
 *   <tr><td>deleteById(T)</td><td>delete(M)</td><td>按实体删除</td></tr>
 *   <tr><td colspan="3"><b>批量操作</b></td></tr>
 *   <tr><td>selectByIds(Collection)</td><td>findByIds(Collection&lt;ID&gt;)</td><td>批量查询</td></tr>
 *   <tr><td>deleteByIds(Collection)</td><td>deleteByIds(Collection&lt;ID&gt;)</td><td>批量删除</td></tr>
 *   <tr><td>insert(Collection)</td><td>saveBatch(Collection&lt;M&gt;)</td><td>批量插入</td></tr>
 *   <tr><td>updateById(Collection)</td><td>updateBatchById(Collection&lt;M&gt;)</td><td>批量更新</td></tr>
 *   <tr><td>insertOrUpdate(Collection)</td><td>insertOrUpdateBatch(Collection&lt;M&gt;)</td><td>批量保存或更新</td></tr>
 *   <tr><td colspan="3"><b>条件查询（Query&lt;P&gt; 替代 Wrapper）</b></td></tr>
 *   <tr><td>selectOne(Wrapper)</td><td>findFirst(Query)</td><td>查询单条</td></tr>
 *   <tr><td>selectList(Wrapper)</td><td>findList(Query)</td><td>查询列表</td></tr>
 *   <tr><td>selectCount(Wrapper)</td><td>count(Query)</td><td>条件计数</td></tr>
 *   <tr><td>selectPage(page, Wrapper)</td><td>page(Query)</td><td>分页查询</td></tr>
 *   <tr><td>selectMaps(Wrapper)</td><td>maps(Query)</td><td>查询 Map 列表</td></tr>
 *   <tr><td>exists(Wrapper)</td><td>exists(Query)</td><td>条件存在判断</td></tr>
 *   <tr><td>update(T, Wrapper)</td><td>update(M, Query)</td><td>条件更新</td></tr>
 *   <tr><td>delete(Wrapper)</td><td>deleteByQuery(Query)</td><td>条件删除</td></tr>
 * </table>
 *
 * @param <M>  聚合根类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@SuppressWarnings("unchecked")
public interface Repository<M extends AggregateRoot<?>, ID extends Serializable> {

    // ========================= 单条 CRUD =========================

    /**
     * \u6309\u6807\u8bc6\u67e5\u627e\u805a\u5408\u6839\uff08\u5bf9\u5e94 {@code selectById}\uff09\u3002
     *
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @return \u6307\u5b9a\u6807\u8bc6\u7684\u805a\u5408\u6839
     */
    Optional<M> findById(ID id);

    /**
     * \u4fdd\u5b58\u805a\u5408\u6839\uff08\u65b0\u5efa\u6216\u66f4\u65b0\uff0c\u5bf9\u5e94 {@code insert} + {@code updateById}\uff09\u3002
     * @return \u4fdd\u5b58\u540e\u7684\u805a\u5408\u6839\uff08\u5305\u542b\u81ea\u589e\u5b57\u6bb5\uff09
     *
     * @param aggregate \u5f85\u5904\u7406\u805a\u5408\u6839
     */
    M save(M aggregate);

    /**
     * \u6309\u4e3b\u952e\u66f4\u65b0\uff08\u5bf9\u5e94 {@code updateById}\uff09\u3002
     *
     * @param aggregate \u5f85\u5904\u7406\u805a\u5408\u6839
     * @return \u66f4\u65b0\u540e\u7684\u805a\u5408\u6839
     */
    default M updateById(M aggregate) {
        return save(aggregate);
    }

    /**
     * \u4e3b\u952e\u5b58\u5728\u66f4\u65b0\uff0c\u5426\u63d2\u5165\uff08\u5bf9\u5e94 {@code insertOrUpdate}\uff09\u3002
     *
     * @param aggregate \u5f85\u5904\u7406\u805a\u5408\u6839
     * @return \u4fdd\u5b58\u6216\u66f4\u65b0\u540e\u7684\u805a\u5408\u6839
     */
    default M insertOrUpdate(M aggregate) {
        return save(aggregate);
    }

    /**
     * \u68c0\u67e5\u6807\u8bc6\u662f\u5426\u5b58\u5728\u3002
     *
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @return \u6807\u8bc6\u5b58\u5728\u65f6\u4e3a true
     */
    default boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    /**
     * \u6309\u6807\u8bc6\u5220\u9664\u805a\u5408\u6839\uff08\u5bf9\u5e94 {@code deleteById}\uff09\u3002
     *
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     */
    default void deleteById(ID id) {
        throw new UnsupportedOperationException("deleteById is not supported by this repository");
    }

    /**
     * \u5220\u9664\u805a\u5408\u6839\uff08\u5bf9\u5e94 {@code deleteById(T)}\uff09\u3002
     *
     * @param aggregate \u5f85\u5904\u7406\u805a\u5408\u6839
     */
    default void delete(M aggregate) {
        if (Objects.nonNull(aggregate)) {
            deleteById((ID) aggregate.id());
        }
    }

    // ========================= 批量操作 =========================

    /**
     * \u6309\u4e3b\u952e\u6279\u91cf\u5220\u9664\uff08\u5bf9\u5e94 {@code deleteByIds}\uff09\u3002
     *
     * @param ids \u805a\u5408\u6839\u6807\u8bc6\u96c6\u5408
     * @return \u5220\u9664\u7684\u8bb0\u5f55\u6570
     */
    default int deleteByIds(Collection<ID> ids) {
        throw new UnsupportedOperationException("deleteByIds is not supported by this repository");
    }

    /**
     * \u6309\u4e3b\u952e\u6279\u91cf\u67e5\u8be2\uff08\u5bf9\u5e94 {@code selectByIds}\uff09\u3002
     *
     * @param ids \u805a\u5408\u6839\u6807\u8bc6\u96c6\u5408
     * @return \u5339\u914d\u7684\u805a\u5408\u6839\u5217\u8868
     */
    default List<M> findByIds(Collection<ID> ids) {
        throw new UnsupportedOperationException("findByIds is not supported by this repository");
    }

    /**
     * \u6279\u91cf\u4fdd\u5b58\uff08\u5bf9\u5e94 {@code insert(Collection)}\uff09\u3002
     *
     * @param aggregates \u5f85\u5904\u7406\u805a\u5408\u6839\u96c6\u5408
     * @return \u4fdd\u5b58\u540e\u7684\u805a\u5408\u6839\u5217\u8868
     */
    default List<M> saveBatch(Collection<M> aggregates) {
        throw new UnsupportedOperationException("saveBatch is not supported by this repository");
    }

    /**
     * \u6279\u91cf\u66f4\u65b0\uff08\u5bf9\u5e94 {@code updateById(Collection)}\uff09\u3002
     *
     * @param aggregates \u5f85\u5904\u7406\u805a\u5408\u6839\u96c6\u5408
     * @return \u66f4\u65b0\u7684\u8bb0\u5f55\u6570
     */
    default int updateBatchById(Collection<M> aggregates) {
        throw new UnsupportedOperationException("updateBatchById is not supported by this repository");
    }

    /**
     * \u6279\u91cf\u4fdd\u5b58\u6216\u66f4\u65b0\uff08\u5bf9\u5e94 {@code insertOrUpdate(Collection)}\uff09\u3002
     *
     * @param aggregates \u5f85\u5904\u7406\u805a\u5408\u6839\u96c6\u5408
     * @return \u4fdd\u5b58\u6216\u66f4\u65b0\u7684\u8bb0\u5f55\u6570
     */
    default int insertOrUpdateBatch(Collection<M> aggregates) {
        throw new UnsupportedOperationException("insertOrUpdateBatch is not supported by this repository");
    }

    // ========================= 无条件查询 =========================

    /**
     * \u67e5\u627e\u7b2c\u4e00\u4e2a\u805a\u5408\u6839\u3002
     *
     * @return \u7b2c\u4e00\u6761\u805a\u5408\u6839
     */
    default Optional<M> findFirst() {
        List<M> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * \u67e5\u627e\u5168\u90e8\u805a\u5408\u6839\u3002
     *
     * @return \u5168\u90e8\u805a\u5408\u6839
     */
    default List<M> findAll() {
        throw new UnsupportedOperationException("findAll is not supported by this repository");
    }

    /**
     * \u7edf\u8ba1\u603b\u6570\u3002
     *
     * @return \u8bb0\u5f55\u6570
     */
    default long count() {
        return findAll().size();
    }

    /**
     * \u662f\u5426\u5b58\u5728\u8bb0\u5f55\u3002
     *
     * @return \u5b58\u5728\u8bb0\u5f55\u65f6\u4e3a true
     */
    default boolean exists() {
        return count() > 0;
    }

    // ========================= 条件查询（基于 Query<M>） =========================

    /**
     * 按条件查找第一个（对应 {@code selectOne(Wrapper)}）。
     *
     * @param query 查询条件
     * @return 查询结果
     */
    default Optional<M> findFirst(Query<M> query) {
        throw new UnsupportedOperationException("findFirst(query) is not supported by this repository");
    }

    /**
     * 按条件查询列表（对应 {@code selectList(Wrapper)}）。
     *
     * @param query 查询条件
     * @return 查询结果
     */
    default List<M> findList(Query<M> query) {
        throw new UnsupportedOperationException("findList(query) is not supported by this repository");
    }

    /**
     * 按条件分页查询（对应 {@code selectPage(page, Wrapper)}）。
     *
     * @param query 查询条件
     * @return 查询结果
     */
    default Page<M> page(Query<M> query) {
        throw new UnsupportedOperationException("page(query) is not supported by this repository");
    }

    /**
     * 按条件计数（对应 {@code selectCount(Wrapper)}）。
     *
     * @param query 查询条件
     * @return 查询结果
     */
    default long count(Query<M> query) {
        return findList(query).size();
    }

    /**
     * 按条件查询是否存在（对应 {@code exists(Wrapper)}）。
     *
     * @param query 查询条件
     * @return 查询结果
     */
    default boolean exists(Query<M> query) {
        return count(query) > 0;
    }

    /**
     * 按条件查询 Map 列表（对应 {@code selectMaps(Wrapper)}）。
     *
     * @param query 查询条件
     * @return 查询结果
     */
    default List<Map<String, Object>> maps(Query<M> query) {
        throw new UnsupportedOperationException("maps(query) is not supported by this repository");
    }

    // ========================= 条件更新 / 删除 =========================

    /**
     * \u6309\u6761\u4ef6\u66f4\u65b0\uff08\u5bf9\u5e94 {@code update(T, Wrapper)}\uff09\u3002
     *
     * @param aggregate \u5f85\u5904\u7406\u805a\u5408\u6839
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u81f3\u5c11\u66f4\u65b0\u4e00\u6761\u8bb0\u5f55\u65f6\u4e3a true
     */
    default boolean update(AggregateRoot<?> aggregate, Query<M> query) {
        throw new UnsupportedOperationException("update(aggregate, query) is not supported by this repository");
    }

    /**
     * \u6309\u6761\u4ef6\u5220\u9664\uff08\u5bf9\u5e94 {@code delete(Wrapper)}\uff09\u3002
     *
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u81f3\u5c11\u5220\u9664\u4e00\u6761\u8bb0\u5f55\u65f6\u4e3a true
     */
    default boolean deleteByQuery(Query<M> query) {
        throw new UnsupportedOperationException("deleteByQuery(query) is not supported by this repository");
    }

    // ========================= 聚合填充 =========================

    /**
     * \u6570\u636e\u805a\u5408\u586b\u5145\uff08\u4ece\u5176\u4ed6\u805a\u5408\u8865\u5145\u6570\u636e\u5230\u5f53\u524d\u805a\u5408\u6839\uff09\u3002
     *
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @param model \u5f85\u586b\u5145\u805a\u5408\u6839
     */
    default void fill(Query<M> query, AggregateRoot<?> model) {
        // 默认空实现，业务方按需覆盖
    }

    /**
     * \u6570\u636e\u6279\u91cf\u805a\u5408\u586b\u5145\u3002
     *
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @param models \u805a\u5408\u6839\u5217\u8868
     */
    default void fill(Query<M> query, List<M> models) {
        for (M model : models) {
            fill(query, model);
        }
    }
}
