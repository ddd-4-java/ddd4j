package io.ddd4j.core.ddd.model;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;

import java.io.Serializable;
import java.util.*;

/**
 * 充血聚合根基类（ddd4j 唯一推荐）。
 * <p>
 * 保留旧 ddd4j {@code Model.save()/update()/delete()/saveOrUpdate()/updateByKey()}
 * 的全部充血语义，但通过 {@link RepositoryRegistry}（基于 {@link io.ddd4j.core.context.Contexts}
 * 的上下文查找）获取仓储实例，彻底消除对 MyBatis 等 ORM 的静态注册表耦合。
 *
 * <h3>充血持久化方法（实例方法）</h3>
 * <pre>{@code
 * Order order = new Order(orderId, total);
 * order.save();         // ← 自动找到 OrderRepository.save(order)
 * order.pay(amount);
 * order.update();       // ← 自动找到 OrderRepository.save(order)
 * order.saveOrUpdate(); // ← 自动找到 OrderRepository.save(order)
 * order.delete();       // ← 自动找到 OrderRepository.delete(order)
 *
 * // 批量
 * List<Order> orders = ...;
 * AggregateRoot.save(orders);   // ← 自动找到 OrderRepository.saveAll(orders)
 * AggregateRoot.delete(query);  // ← 自动找到 OrderRepository.deleteByQuery(query)
 * }</pre>
 *
 * <h3>充血查询方法（静态）</h3>
 * <pre>{@code
 * Optional<Order> found = AggregateRoot.get(Order.class, orderId);
 * Optional<Order> first = AggregateRoot.one(Order.class);
 * List<Order> all = AggregateRoot.list(Order.class);
 * Page<Order> page = AggregateRoot.page(query);
 * int count = AggregateRoot.count(query);
 * boolean exists = AggregateRoot.exist(query);
 * }</pre>
 *
 * <h3>事件能力</h3>
 * <pre>{@code
 * public class Order extends AggregateRoot<OrderId> {
 *     public void pay(Money amount) {
 *         registerEvent(new OrderPaidEvent(id, amount));
 *     }
 * }
 * // order.domainEvents() → [OrderPaidEvent]
 * }</pre>
 *
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class AggregateRoot<ID extends Serializable> implements Entity<ID> {

    /** \u805a\u5408\u5f53\u524d\u5c1a\u672a\u6e05\u7406\u7684\u9886\u57df\u4e8b\u4ef6\u3002 */
    private transient List<DomainEvent<?>> domainEvents = new ArrayList<>();

    // ========================= 充血持久化（实例方法） =========================

    /**
     * \u6279\u91cf\u4fdd\u5b58\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @param models \u805a\u5408\u6839\u5217\u8868
     * @return \u4fdd\u5b58\u7ed3\u679c\uff1b\u6279\u91cf\u8c03\u7528\u5728\u5217\u8868\u975e\u7a7a\u65f6\u8fd4\u56de true
     */
    public static <M extends AggregateRoot<?>> boolean save(List<M> models) {
        if (Objects.isNull(models) || models.isEmpty()) {
            return false;
        }
        Repository repo = RepositoryRegistry.repository(models.get(0).getClass());
        for (AggregateRoot m : models) {
            repo.save(m);
        }
        return true;
    }

    /**
     * \u6279\u91cf\u66f4\u65b0\uff08\u4ec5\u66f4\u65b0\uff0c\u4e0d\u63d2\u5165\uff1b\u9010\u6761\u59d4\u6258 {@link Repository#updateById(Object)}\uff09\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @param models \u805a\u5408\u6839\u5217\u8868
     * @return \u66f4\u65b0\u7ed3\u679c
     */
    public static <M extends AggregateRoot<?>> boolean update(List<M> models) {
        if (Objects.isNull(models) || models.isEmpty()) {
            return false;
        }
        Repository repo = RepositoryRegistry.repository(models.get(0).getClass());
        for (AggregateRoot m : models) {
            repo.updateById(m);
        }
        return true;
    }

    /**
     * \u6309\u67e5\u8be2\u6761\u4ef6\u5220\u9664\u3002
     *
     * @param <Q> \u67e5\u8be2\u6761\u4ef6\u7c7b\u578b
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u81f3\u5c11\u5220\u9664\u4e00\u6761\u8bb0\u5f55\u65f6\u4e3a true
     */
    public static <Q extends Query> boolean delete(Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repositoryForQuery(query.getClass());
        return ((Repository) repo).deleteByQuery(query);
    }

    /**
     * \u6309 ID \u67e5\u627e\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @param <ID> \u805a\u5408\u6839\u6807\u8bc6\u7c7b\u578b
     * @param modelClass \u805a\u5408\u6839\u7c7b\u578b
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @return \u6307\u5b9a\u6807\u8bc6\u7684\u805a\u5408\u6839
     */
    public static <M extends AggregateRoot<?>, ID extends Serializable>
    Optional<M> get(Class<M> modelClass, ID id) {
        Repository repo = RepositoryRegistry.repository(modelClass);
        return repo.findById(id);
    }

    /**
     * \u67e5\u627e\u7b2c\u4e00\u4e2a\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @param modelClass \u805a\u5408\u6839\u7c7b\u578b
     * @return \u7b2c\u4e00\u6761\u805a\u5408\u6839
     */
    public static <M extends AggregateRoot<?>> Optional<M> one(Class<M> modelClass) {
        Repository repo = RepositoryRegistry.repository(modelClass);
        return ((Repository) repo).findFirst();
    }

    /**
     * \u5217\u51fa\u5168\u90e8\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @param modelClass \u805a\u5408\u6839\u7c7b\u578b
     * @return \u5168\u90e8\u805a\u5408\u6839
     */
    public static <M extends AggregateRoot<?>> List<M> list(Class<M> modelClass) {
        Repository repo = RepositoryRegistry.repository(modelClass);
        return ((Repository) repo).findAll();
    }

    // ========================= 充血持久化（静态批量） =========================

    /**
     * \u5206\u9875\u67e5\u8be2\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @param <Q> \u67e5\u8be2\u6761\u4ef6\u7c7b\u578b
     * @param modelClass \u805a\u5408\u6839\u7c7b\u578b
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u5206\u9875\u67e5\u8be2\u7ed3\u679c
     */
    public static <M extends AggregateRoot<?>, Q extends Query>
    Page<M> page(Class<M> modelClass, Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repository(modelClass);
        return ((Repository) repo).page(query);
    }

    /**
     * \u8ba1\u6570\u3002
     *
     * @param <Q> \u67e5\u8be2\u6761\u4ef6\u7c7b\u578b
     * @param modelClass \u805a\u5408\u6839\u7c7b\u578b
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u5339\u914d\u8bb0\u5f55\u6570
     */
    public static <Q extends Query> int count(Class<? extends AggregateRoot<?>> modelClass, Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repository(modelClass);
        return (int) ((Repository) repo).count(query);
    }

    /**
     * \u662f\u5426\u5b58\u5728\u3002
     *
     * @param <Q> \u67e5\u8be2\u6761\u4ef6\u7c7b\u578b
     * @param modelClass \u805a\u5408\u6839\u7c7b\u578b
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u5b58\u5728\u5339\u914d\u8bb0\u5f55\u65f6\u4e3a true
     */
    public static <Q extends Query> boolean exist(Class<? extends AggregateRoot<?>> modelClass, Q query) {
        return count(modelClass, query) > 0;
    }

    // ========================= 充血查询（静态） =========================

    /**
     * \u5145\u8840\u4fdd\u5b58\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @return \u4fdd\u5b58\u540e\u7684\u805a\u5408\u6839
     */
    public <M extends AggregateRoot<ID>> M save() {
        return (M) repository().save(this);
    }

    /**
     * \u5145\u8840\u66f4\u65b0\uff08\u4ec5\u6309\u4e3b\u952e\u66f4\u65b0\uff0c\u4e0d\u63d2\u5165\uff1b\u59d4\u6258 {@link Repository#updateById(Object)}\uff09\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @return \u66f4\u65b0\u540e\u7684\u805a\u5408\u6839
     */
    public <M extends AggregateRoot<ID>> M update() {
        return (M) repository().updateById(this);
    }

    /**
     * \u5145\u8840\u4fdd\u5b58\u6216\u66f4\u65b0\uff08\u4e3b\u952e\u5b58\u5728\u5219\u66f4\u65b0\uff0c\u5426\u5219\u63d2\u5165\uff1b\u59d4\u6258 {@link Repository#insertOrUpdate(Object)}\uff09\u3002
     *
     * @param <M> \u805a\u5408\u6839\u7c7b\u578b
     * @return \u4fdd\u5b58\u6216\u66f4\u65b0\u540e\u7684\u805a\u5408\u6839
     */
    public <M extends AggregateRoot<ID>> M saveOrUpdate() {
        return (M) repository().insertOrUpdate(this);
    }

    /**
     * 充血删除。
     */
    public void delete() {
        repository().delete(this);
    }

    /**
     * \u5145\u8840\u6761\u4ef6\u66f4\u65b0\uff08\u6309\u67e5\u8be2\u6761\u4ef6\u66f4\u65b0\uff09\u3002
     *
     * @param <Q> \u67e5\u8be2\u6761\u4ef6\u7c7b\u578b
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u81f3\u5c11\u66f4\u65b0\u4e00\u6761\u8bb0\u5f55\u65f6\u4e3a true
     */
    public <Q extends Query> boolean update(Q query) {
        query.with();
        Repository repo = repository();
        if (Objects.nonNull(repo)) {
            return ((Repository) repo).update(this, query);
        }
        throw new BizRuntimeException("Repository does not support update(query)");
    }

    /**
     * \u5145\u8840\u805a\u5408\u586b\u5145\uff08\u4ece\u5176\u4ed6\u805a\u5408\u8865\u5145\u6570\u636e\uff09\u3002
     *
     * @param <Q> \u67e5\u8be2\u6761\u4ef6\u7c7b\u578b
     * @param query \u67e5\u8be2\u6761\u4ef6
     */
    public <Q extends Query> void fill(Q query) {
        Repository repo = repository();
        if (Objects.nonNull(repo)) {
            ((Repository) repo).fill(query, this);
        }
    }

    // ========================= 事件管理 =========================

    /**
     * \u6ce8\u518c\u9886\u57df\u4e8b\u4ef6\u3002
     *
     * @param event \u9886\u57df\u4e8b\u4ef6
     */
    protected void registerEvent(DomainEvent<?> event) {
        Objects.requireNonNull(event, "event must not be null");
        mutableDomainEvents().add(event);
    }

    /**
     * \u8fd4\u56de\u672a\u63d0\u4ea4\u7684\u9886\u57df\u4e8b\u4ef6\uff08\u4e0d\u53ef\u53d8\u89c6\u56fe\uff09\u3002
     *
     * @return \u672a\u63d0\u4ea4\u9886\u57df\u4e8b\u4ef6\u7684\u4e0d\u53ef\u53d8\u89c6\u56fe
     */
    public List<DomainEvent<?>> domainEvents() {
        return Collections.unmodifiableList(mutableDomainEvents());
    }

    /**
     * \u8fd4\u56de\u5e76\u6e05\u7a7a\u672a\u63d0\u4ea4\u7684\u9886\u57df\u4e8b\u4ef6\u3002
     *
     * @return \u53d6\u51fa\u7684\u672a\u63d0\u4ea4\u9886\u57df\u4e8b\u4ef6
     */
    public List<DomainEvent<?>> pullDomainEvents() {
        List<DomainEvent<?>> events = List.copyOf(mutableDomainEvents());
        clearDomainEvents();
        return events;
    }

    /**
     * 清空未提交的领域事件。
     */
    public void clearDomainEvents() {
        mutableDomainEvents().clear();
    }

    /**
     * \u662f\u5426\u5b58\u5728\u672a\u63d0\u4ea4\u7684\u9886\u57df\u4e8b\u4ef6\u3002
     *
     * @return \u5b58\u5728\u672a\u63d0\u4ea4\u9886\u57df\u4e8b\u4ef6\u65f6\u4e3a true
     */
    public boolean hasDomainEvents() {
        return !mutableDomainEvents().isEmpty();
    }

    /**
     * \u5728\u7b2c\u4e00\u6b21\u5199\u5165\u524d\u521d\u59cb\u5316\u9886\u57df\u4e8b\u4ef6\u5217\u8868\u3002
     * @return \u5f53\u524d\u805a\u5408\u53ef\u5199\u5165\u7684\u9886\u57df\u4e8b\u4ef6\u5217\u8868
     */
    private List<DomainEvent<?>> mutableDomainEvents() {
        if (Objects.isNull(domainEvents)) {
            domainEvents = new ArrayList<>();
        }
        return domainEvents;
    }

    // ========================= 仓储查找 =========================

    /**
     * \u901a\u8fc7 {@link RepositoryRegistry} \u67e5\u627e\u5f53\u524d\u805a\u5408\u6839\u7c7b\u578b\u7684\u4ed3\u50a8\u5b9e\u4f8b\u3002
     *
     * @return \u5f53\u524d\u805a\u5408\u6839\u7c7b\u578b\u7684\u4ed3\u50a8
     */
    @SuppressWarnings("rawtypes")
    protected Repository repository() {
        return RepositoryRegistry.repository(this.getClass());
    }

}
