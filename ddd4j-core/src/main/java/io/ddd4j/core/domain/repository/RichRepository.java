package io.ddd4j.core.domain.repository;

import io.ddd4j.core.domain.contract.Page;
import io.ddd4j.core.domain.query.Query;
import io.ddd4j.core.domain.model.AggregateRoot;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 富查询仓储 SPI（扩展 {@link Repository}，支持充血查询 + 条件查询 + 聚合填充）。
 * <p>
 * 仓储实现可选择是否实现此接口。实现后，聚合根的全部充血方法可用：
 * <ul>
 *   <li>{@code AggregateRoot.one(Class)} / {@code AggregateRoot.list(Class)}</li>
 *   <li>{@code AggregateRoot.page(Class, query)} / {@code AggregateRoot.count(Class, query)}</li>
 *   <li>{@code aggregate.update(query)} / {@code AggregateRoot.delete(query)}</li>
 *   <li>{@code aggregate.fill(query)}</li>
 * </ul>
 *
 * @param <M>  聚合根类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public interface RichRepository<M extends AggregateRoot<?>, ID extends Serializable> extends Repository<M, ID> {

    /**
     * 查找第一个聚合根。
     */
    Optional<M> findFirst();

    /**
     * 查找全部聚合根。
     */
    List<M> findAll();

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

    // ========================= 条件查询（基于 Query） =========================

    /**
     * 按条件分页查询。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    Page<M> page(Query query);

    /**
     * 按条件计数。
     *
     * @param query 查询条件
     * @return 数量
     */
    long count(Query query);

    /**
     * 按条件查找第一个。
     *
     * @param query 查询条件
     * @return 第一个聚合根 Optional
     */
    Optional<M> findFirst(Query query);

    /**
     * 按条件查询列表。
     *
     * @param query 查询条件
     * @return 聚合根列表
     */
    List<M> findList(Query query);

    /**
     * 按条件查询 Map 列表。
     *
     * @param query 查询条件
     * @return Map 结果列表
     */
    default List<Map<String, Object>> maps(Query query) {
        throw new UnsupportedOperationException("maps(query) is not supported by this repository");
    }

    /**
     * 按条件查询是否存在。
     *
     * @param query 查询条件
     * @return {@code true} 表示存在
     */
    default boolean exists(Query query) {
        return count(query) > 0;
    }

    // ========================= 条件更新 / 删除 =========================

    /**
     * 按条件更新。
     *
     * @param aggregate 聚合根（提供更新值）
     * @param query     查询条件
     * @return {@code true} 表示更新成功
     */
    boolean update(AggregateRoot<?> aggregate, Query query);

    /**
     * 按条件删除。
     *
     * @param query 查询条件
     * @return {@code true} 表示删除成功
     */
    boolean deleteByQuery(Query query);

    // ========================= 聚合填充 =========================

    /**
     * 数据聚合填充（从其他聚合补充数据到当前聚合根）。
     *
     * @param query 查询条件（指定填充哪些数据）
     * @param model 被填充的聚合根
     */
    void fill(Query query, AggregateRoot<?> model);

    /**
     * 数据批量聚合填充。
     *
     * @param query  查询条件
     * @param models 被填充的聚合根列表
     */
    default void fill(Query query, List<M> models) {
        for (M model : models) {
            fill(query, model);
        }
    }
}
