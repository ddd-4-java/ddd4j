package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.ddd.model.AggregateRoot;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * 统一的领域仓储接口（ddd4j 唯一推荐）。
 * <p>
 * 取代以下三个历史接口：
 * <ul>
 *   <li>旧 ActiveRecord 风格 BaseRepository（CRUD + 静态注册表）</li>
 *   <li>{@code io.ddd4j.core.ddd.repository.Repository}（Optional 风格）</li>
 *   <li>{@code io.ddd4j.core.ddd.model.DomainObjectMapper}（含 delete/existsById 默认方法）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>聚合根导向</b>：仓储只针对 {@link AggregateRoot}，不针对 Entity/PO（CRUD 走聚合根）</li>
 *   <li><b>无状态接口</b>：无静态注册表，由 DI 容器（Spring/CDI/Guice）注入实现</li>
 *   <li><b>Optional 风格</b>：返回 {@link Optional} 而非 null，避免 NPE</li>
 *   <li><b>事件溯源友好</b>：聚合根自带 {@code domainEvents()}，仓储实现可选择性消费（实现 {@link EventSourcingRepository}）</li>
 * </ul>
 *
 * <h3>多种持久化实现</h3>
 * <table border="1">
 *   <tr><th>实现</th><th>使用场景</th><th>扩展点</th></tr>
 *   <tr><td>MyBatis-Plus 适配</td><td>传统 CRUD</td><td>本接口</td></tr>
 *   <tr><td>Hibernate Panache</td><td>Quarkus</td><td>本接口</td></tr>
 *   <tr><td>JDBI</td><td>Javalin</td><td>本接口</td></tr>
 *   <tr><td>EventSourcing 适配</td><td>事件溯源</td><td>{@link EventSourcingRepository}</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * &#64;DomainRepository
 * public interface OrderRepository extends Repository<Order, OrderId> {
 *     // 业务侧可扩展查询方法
 *     List<Order> findByStatus(OrderStatus status);
 * }
 * }</pre>
 *
 * @param <M>  聚合根类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface Repository<M extends AggregateRoot<?>, ID extends Serializable> {

    /**
     * 按标识查找聚合根。
     *
     * @param id 聚合根标识
     * @return 聚合根 Optional（不存在时返回 {@link Optional#empty()}）
     */
    Optional<M> findById(ID id);

    /**
     * 保存聚合根（新建或更新）。
     * <p>
     * 实现要求：保存前应处理 {@code aggregate.domainEvents()}（持久化或发布）。
     *
     * @param aggregate 聚合根
     * @return 保存后的聚合根（包含自增字段）
     */
    M save(M aggregate);

    /**
     * 检查聚合根是否存在。
     *
     * @param id 聚合根标识
     * @return {@code true} 表示存在
     */
    default boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    /**
     * 删除聚合根。
     *
     * @param aggregate 聚合根
     */
    @SuppressWarnings("unchecked")
    default void delete(M aggregate) {
        if (Objects.nonNull(aggregate)) {
            deleteById((ID) aggregate.id());
        }
    }

    /**
     * 按标识删除聚合根。
     *
     * @param id 聚合根标识
     */
    default void deleteById(ID id) {
        throw new UnsupportedOperationException("deleteById is not supported by this repository");
    }
}
