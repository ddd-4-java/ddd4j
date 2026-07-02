package io.ddd4j.core.ddd.model;

import io.ddd4j.core.contract.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.ddd.repository.RichRepository;
import io.ddd4j.core.exception.BizRuntimeException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private transient List<DomainEvent<?>> domainEvents = new ArrayList<>();

    // ========================= 充血持久化（实例方法） =========================

    /**
     * 充血保存。
     */
    public <M extends AggregateRoot<ID>> M save() {
        return (M) repository().save(this);
    }

    /**
     * 充血更新。
     */
    public <M extends AggregateRoot<ID>> M update() {
        return (M) repository().save(this);
    }

    /**
     * 充血保存或更新。
     */
    public <M extends AggregateRoot<ID>> M saveOrUpdate() {
        return (M) repository().save(this);
    }

    /**
     * 充血删除。
     */
    public void delete() {
        repository().delete(this);
    }

    /**
     * 充血条件更新（按查询条件更新）。
     */
    public <Q extends Query> boolean update(Q query) {
        query.with();
        Repository<AggregateRoot<ID>, ?> repo = repository();
        if (repo instanceof RichRepository) {
            return ((RichRepository<?, ?>) repo).update(this, query);
        }
        throw new BizRuntimeException("Repository does not support update(query)");
    }

    /**
     * 充血聚合填充（从其他聚合补充数据）。
     */
    public <Q extends Query> void fill(Q query) {
        Repository<AggregateRoot<ID>, ?> repo = repository();
        if (repo instanceof RichRepository) {
            ((RichRepository<?, ?>) repo).fill(query, this);
        }
    }

    // ========================= 充血持久化（静态批量） =========================

    /**
     * 批量保存。
     */
    public static <M extends AggregateRoot<?>> boolean save(List<M> models) {
        if (models == null || models.isEmpty()) {
            return false;
        }
        Repository repo = RepositoryRegistry.repository(models.get(0).getClass());
        for (AggregateRoot m : models) {
            repo.save(m);
        }
        return true;
    }

    /**
     * 批量更新。
     */
    public static <M extends AggregateRoot<?>> boolean update(List<M> models) {
        return save(models);
    }

    /**
     * 按查询条件删除。
     */
    public static <Q extends Query> boolean delete(Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repositoryForQuery(query.getClass());
        if (repo instanceof RichRepository) {
            return ((RichRepository<?, ?>) repo).deleteByQuery(query);
        }
        throw new BizRuntimeException("Repository for {} does not support delete(query)", query.getClass().getSimpleName());
    }

    // ========================= 充血查询（静态） =========================

    /**
     * 按 ID 查找。
     */
    public static <M extends AggregateRoot<?>, ID extends Serializable>
    Optional<M> get(Class<M> modelClass, ID id) {
        Repository repo = RepositoryRegistry.repository(modelClass);
        return repo.findById(id);
    }

    /**
     * 查找第一个。
     */
    public static <M extends AggregateRoot<?>> Optional<M> one(Class<M> modelClass) {
        Repository repo = RepositoryRegistry.repository(modelClass);
        if (repo instanceof RichRepository) {
            return ((RichRepository<M, ?>) repo).findFirst();
        }
        throw new BizRuntimeException("Repository for {} does not support one()", modelClass.getSimpleName());
    }

    /**
     * 列出全部。
     */
    public static <M extends AggregateRoot<?>> List<M> list(Class<M> modelClass) {
        Repository repo = RepositoryRegistry.repository(modelClass);
        if (repo instanceof RichRepository) {
            return ((RichRepository<M, ?>) repo).findAll();
        }
        throw new BizRuntimeException("Repository for {} does not support list()", modelClass.getSimpleName());
    }

    /**
     * 分页查询。
     */
    public static <M extends AggregateRoot<?>, Q extends Query>
    Page<M> page(Class<M> modelClass, Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repository(modelClass);
        if (repo instanceof RichRepository) {
            return ((RichRepository<M, ?>) repo).page(query);
        }
        throw new BizRuntimeException("Repository for {} does not support page()", modelClass.getSimpleName());
    }

    /**
     * 计数。
     */
    public static <Q extends Query> int count(Class<? extends AggregateRoot<?>> modelClass, Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repository(modelClass);
        if (repo instanceof RichRepository) {
            return (int) ((RichRepository<?, ?>) repo).count(query);
        }
        throw new BizRuntimeException("Repository for {} does not support count()", modelClass.getSimpleName());
    }

    /**
     * 是否存在。
     */
    public static <Q extends Query> boolean exist(Class<? extends AggregateRoot<?>> modelClass, Q query) {
        return count(modelClass, query) > 0;
    }

    // ========================= 事件管理 =========================

    /**
     * 注册领域事件。
     */
    protected void registerEvent(DomainEvent<?> event) {
        Objects.requireNonNull(event, "event must not be null");
        mutableDomainEvents().add(event);
    }

    /**
     * 返回未提交的领域事件（不可变视图）。
     */
    public List<DomainEvent<?>> domainEvents() {
        return Collections.unmodifiableList(mutableDomainEvents());
    }

    /**
     * 返回并清空未提交的领域事件。
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
     * 是否存在未提交的领域事件。
     */
    public boolean hasDomainEvents() {
        return !mutableDomainEvents().isEmpty();
    }

    private List<DomainEvent<?>> mutableDomainEvents() {
        if (Objects.isNull(domainEvents)) {
            domainEvents = new ArrayList<>();
        }
        return domainEvents;
    }

    // ========================= 仓储查找 =========================

    /**
     * 通过 {@link RepositoryRegistry} 查找当前聚合根类型的仓储实例。
     */
    protected <M extends AggregateRoot<ID>> Repository<M, ?> repository() {
        return RepositoryRegistry.repository((Class<M>) this.getClass());
    }

}
