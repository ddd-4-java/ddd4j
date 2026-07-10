package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 统一的领域仓储接口（对齐 MyBatis-Plus {@code BaseMapper} 全部常用方法）。
 *
 * <p>合并了旧 {@code Repository}（基础 CRUD）和 {@code RichRepository}（充血查询），
 * 因为 Lambda 改造后 {@link Query} 已绑定 PO 类型，无需区分两个接口。
 *
 * <h3>方法对照 BaseMapper</h3>
 * <table border="1">
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
     * 按标识查找聚合根（对应 {@code selectById}）。
     */
    Optional<M> findById(ID id);

    /**
     * 保存聚合根（新建或更新，对应 {@code insert} + {@code updateById}）。
     *
     * @return 保存后的聚合根（包含自增字段）
     */
    M save(M aggregate);

    /**
     * 按主键更新（对应 {@code updateById}）。
     */
    default M updateById(M aggregate) {
        return save(aggregate);
    }

    /**
     * 主键存在更新，否插入（对应 {@code insertOrUpdate}）。
     */
    default M insertOrUpdate(M aggregate) {
        return save(aggregate);
    }

    /**
     * 检查标识是否存在。
     */
    default boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    /**
     * 按标识删除聚合根（对应 {@code deleteById}）。
     */
    default void deleteById(ID id) {
        throw new UnsupportedOperationException("deleteById is not supported by this repository");
    }

    /**
     * 删除聚合根（对应 {@code deleteById(T)}）。
     */
    default void delete(M aggregate) {
        if (Objects.nonNull(aggregate)) {
            deleteById((ID) aggregate.id());
        }
    }

    // ========================= 批量操作 =========================

    /**
     * 按主键批量删除（对应 {@code deleteByIds}）。
     */
    default int deleteByIds(Collection<ID> ids) {
        throw new UnsupportedOperationException("deleteByIds is not supported by this repository");
    }

    /**
     * 按主键批量查询（对应 {@code selectByIds}）。
     */
    default List<M> findByIds(Collection<ID> ids) {
        throw new UnsupportedOperationException("findByIds is not supported by this repository");
    }

    /**
     * 批量保存（对应 {@code insert(Collection)}）。
     */
    default List<M> saveBatch(Collection<M> aggregates) {
        throw new UnsupportedOperationException("saveBatch is not supported by this repository");
    }

    /**
     * 批量更新（对应 {@code updateById(Collection)}）。
     */
    default int updateBatchById(Collection<M> aggregates) {
        throw new UnsupportedOperationException("updateBatchById is not supported by this repository");
    }

    /**
     * 批量保存或更新（对应 {@code insertOrUpdate(Collection)}）。
     */
    default int insertOrUpdateBatch(Collection<M> aggregates) {
        throw new UnsupportedOperationException("insertOrUpdateBatch is not supported by this repository");
    }

    // ========================= 无条件查询 =========================

    /**
     * 查找第一个聚合根。
     */
    default Optional<M> findFirst() {
        List<M> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * 查找全部聚合根。
     */
    default List<M> findAll() {
        throw new UnsupportedOperationException("findAll is not supported by this repository");
    }

    /**
     * 统计总数。
     */
    default long count() {
        return findAll().size();
    }

    /**
     * 是否存在记录。
     */
    default boolean exists() {
        return count() > 0;
    }

    // ========================= 条件查询（基于 Query<M>） =========================

    /**
     * 按条件查找第一个（对应 {@code selectOne(Wrapper)}）。
     * @param query 查询条件
     * @return 查询结果
     */
    default Optional<M> findFirst(Query<M> query) {
        throw new UnsupportedOperationException("findFirst(query) is not supported by this repository");
    }

    /**
     * 按条件查询列表（对应 {@code selectList(Wrapper)}）。
     * @param query 查询条件
     * @return 查询结果
     */
    default List<M> findList(Query<M> query) {
        throw new UnsupportedOperationException("findList(query) is not supported by this repository");
    }

    /**
     * 按条件分页查询（对应 {@code selectPage(page, Wrapper)}）。
     * @param query 查询条件
     * @return 查询结果
     */
    default Page<M> page(Query<M> query) {
        throw new UnsupportedOperationException("page(query) is not supported by this repository");
    }

    /**
     * 按条件计数（对应 {@code selectCount(Wrapper)}）。
     * @param query 查询条件
     * @return 查询结果
     */
    default long count(Query<M> query) {
        return findList(query).size();
    }

    /**
     * 按条件查询是否存在（对应 {@code exists(Wrapper)}）。
     * @param query 查询条件
     * @return 查询结果
     */
    default boolean exists(Query<M> query) {
        return count(query) > 0;
    }

    /**
     * 按条件查询 Map 列表（对应 {@code selectMaps(Wrapper)}）。
     * @param query 查询条件
     * @return 查询结果
     */
    default List<Map<String, Object>> maps(Query<M> query) {
        throw new UnsupportedOperationException("maps(query) is not supported by this repository");
    }

    // ========================= 条件更新 / 删除 =========================

    /**
     * 按条件更新（对应 {@code update(T, Wrapper)}）。
     */
    default boolean update(AggregateRoot<?> aggregate, Query<M> query) {
        throw new UnsupportedOperationException("update(aggregate, query) is not supported by this repository");
    }

    /**
     * 按条件删除（对应 {@code delete(Wrapper)}）。
     */
    default boolean deleteByQuery(Query<M> query) {
        throw new UnsupportedOperationException("deleteByQuery(query) is not supported by this repository");
    }

    // ========================= 聚合填充 =========================

    /**
     * 数据聚合填充（从其他聚合补充数据到当前聚合根）。
     */
    default void fill(Query<M> query, AggregateRoot<?> model) {
        // 默认空实现，业务方按需覆盖
    }

    /**
     * 数据批量聚合填充。
     */
    default void fill(Query<M> query, List<M> models) {
        for (M model : models) {
            fill(query, model);
        }
    }
}
