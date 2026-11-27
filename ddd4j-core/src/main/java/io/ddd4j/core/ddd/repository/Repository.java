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
 * 聚合仓储与持久化适配器的公共契约。
 *
 * <p>实现必须提供按标识读取和保存。业务层应通过具体聚合的仓储端口表达业务查询及事务要求。
 * 分页、批量和条件操作属于适配器能力，直接使用前须确认实现支持。缺省抛出
 * {@link UnsupportedOperationException} 的方法不属于最小实现契约。
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
     * 按标识查找聚合根（对应 {@code selectById}）。
     *
     * @param id 聚合根标识
     * @return 指定标识的聚合根
     */
    Optional<M> findById(ID id);

    /**
     * 保存聚合根，由具体实现定义新建和更新的语义。
     *
     * @param aggregate 待处理聚合根
     * @return 保存后的聚合根（包含自增字段）
     */
    M save(M aggregate);

    /**
     * 按主键更新（对应 {@code updateById}）。
     *
     * @param aggregate 待处理聚合根
     * @return 更新后的聚合根
     */
    default M updateById(M aggregate) {
        return save(aggregate);
    }

    /**
     * 主键存在更新，否插入（对应 {@code insertOrUpdate}）。
     *
     * @param aggregate 待处理聚合根
     * @return 保存或更新后的聚合根
     */
    default M insertOrUpdate(M aggregate) {
        return save(aggregate);
    }

    /**
     * 检查标识是否存在。
     *
     * @param id 聚合根标识
     * @return 标识存在时为 true
     */
    default boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    /**
     * 按标识删除聚合根（对应 {@code deleteById}）。
     *
     * @param id 聚合根标识
     */
    default void deleteById(ID id) {
        throw new UnsupportedOperationException("deleteById is not supported by this repository");
    }

    /**
     * 删除聚合根（对应 {@code deleteById(T)}）。
     *
     * @param aggregate 待处理聚合根
     */
    default void delete(M aggregate) {
        if (Objects.nonNull(aggregate)) {
            deleteById((ID) aggregate.id());
        }
    }

    // ========================= 批量操作 =========================

    /**
     * 按主键批量删除（对应 {@code deleteByIds}）。
     *
     * @param ids 聚合根标识集合
     * @return 删除的记录数
     */
    default int deleteByIds(Collection<ID> ids) {
        throw new UnsupportedOperationException("deleteByIds is not supported by this repository");
    }

    /**
     * 按主键批量查询（对应 {@code selectByIds}）。
     *
     * @param ids 聚合根标识集合
     * @return 匹配的聚合根列表
     */
    default List<M> findByIds(Collection<ID> ids) {
        throw new UnsupportedOperationException("findByIds is not supported by this repository");
    }

    /**
     * 批量保存（对应 {@code insert(Collection)}）。
     *
     * @param aggregates 待处理聚合根集合
     * @return 保存后的聚合根列表
     */
    default List<M> saveBatch(Collection<M> aggregates) {
        throw new UnsupportedOperationException("saveBatch is not supported by this repository");
    }

    /**
     * 批量更新（对应 {@code updateById(Collection)}）。
     *
     * @param aggregates 待处理聚合根集合
     * @return 更新的记录数
     */
    default int updateBatchById(Collection<M> aggregates) {
        throw new UnsupportedOperationException("updateBatchById is not supported by this repository");
    }

    /**
     * 批量保存或更新（对应 {@code insertOrUpdate(Collection)}）。
     *
     * @param aggregates 待处理聚合根集合
     * @return 保存或更新的记录数
     */
    default int insertOrUpdateBatch(Collection<M> aggregates) {
        throw new UnsupportedOperationException("insertOrUpdateBatch is not supported by this repository");
    }

    // ========================= 无条件查询 =========================

    /**
     * 查找第一个聚合根。
     *
     * @return 第一条聚合根
     */
    default Optional<M> findFirst() {
        List<M> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * 查找全部聚合根。
     *
     * @return 全部聚合根
     */
    default List<M> findAll() {
        throw new UnsupportedOperationException("findAll is not supported by this repository");
    }

    /**
     * 统计总数。
     *
     * @return 记录数
     */
    default long count() {
        return findAll().size();
    }

    /**
     * 是否存在记录。
     *
     * @return 存在记录时为 true
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
     * 按条件更新（对应 {@code update(T, Wrapper)}）。
     *
     * @param aggregate 待处理聚合根
     * @param query     查询条件
     * @return 至少更新一条记录时为 true
     */
    default boolean update(AggregateRoot<?> aggregate, Query<M> query) {
        throw new UnsupportedOperationException("update(aggregate, query) is not supported by this repository");
    }

    /**
     * 按条件删除（对应 {@code delete(Wrapper)}）。
     *
     * @param query 查询条件
     * @return 至少删除一条记录时为 true
     */
    default boolean deleteByQuery(Query<M> query) {
        throw new UnsupportedOperationException("deleteByQuery(query) is not supported by this repository");
    }

    // ========================= 聚合填充 =========================

    /**
     * 数据聚合填充（从其他聚合补充数据到当前聚合根）。
     *
     * @param query 查询条件
     * @param model 待填充聚合根
     */
    default void fill(Query<M> query, AggregateRoot<?> model) {
        // 默认空实现，业务方按需覆盖
    }

    /**
     * 数据批量聚合填充。
     *
     * @param query  查询条件
     * @param models 聚合根列表
     */
    default void fill(Query<M> query, List<M> models) {
        for (M model : models) {
            fill(query, model);
        }
    }
}
