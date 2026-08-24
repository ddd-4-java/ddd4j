package io.ddd4j.core.ddd.model;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;

import java.io.Serializable;
import java.lang.reflect.Method;
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
 *     public void create(Money total) {
 *         apply(new OrderCreatedEvent(id(), total));   // 反射派发 + 未提交事件入队
 *     }
 *
 *     &#64;EventHandler
 *     private void on(OrderCreatedEvent event) {
 *         this.total = event.getTotal();
 *     }
 *
 *     public void notify(Object notification) {
 *         registerEvent(new OrderNotifiedEvent(id())); // 仅入队，不派发
 *     }
 * }
 * // order.domainEvents() → [OrderCreatedEvent, OrderNotifiedEvent]
 * // loadFromHistory(history) 重建状态且不入队（ignoreOnReplay 处理器跳过）
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
     * 批量保存。
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
     * 批量更新（仅更新，不插入；逐条委托 {@link Repository#updateById(Object)}）。
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
     * 按查询条件删除。
     */
    public static <Q extends Query> boolean delete(Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repositoryForQuery(query.getClass());
        return ((Repository) repo).deleteByQuery(query);
    }

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
        return ((Repository) repo).findFirst();
    }

    /**
     * 列出全部。
     */
    public static <M extends AggregateRoot<?>> List<M> list(Class<M> modelClass) {
        Repository repo = RepositoryRegistry.repository(modelClass);
        return ((Repository) repo).findAll();
    }

    // ========================= 充血持久化（静态批量） =========================

    /**
     * 分页查询。
     */
    public static <M extends AggregateRoot<?>, Q extends Query>
    Page<M> page(Class<M> modelClass, Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repository(modelClass);
        return ((Repository) repo).page(query);
    }

    /**
     * 计数。
     */
    public static <Q extends Query> int count(Class<? extends AggregateRoot<?>> modelClass, Q query) {
        query.with();
        Repository repo = RepositoryRegistry.repository(modelClass);
        return (int) ((Repository) repo).count(query);
    }

    /**
     * 是否存在。
     */
    public static <Q extends Query> boolean exist(Class<? extends AggregateRoot<?>> modelClass, Q query) {
        return count(modelClass, query) > 0;
    }

    // ========================= 充血查询（静态） =========================

    /**
     * 充血保存。
     */
    public <M extends AggregateRoot<ID>> M save() {
        return (M) repository().save(this);
    }

    /**
     * 充血更新（仅按主键更新，不插入；委托 {@link Repository#updateById(Object)}）。
     */
    public <M extends AggregateRoot<ID>> M update() {
        return (M) repository().updateById(this);
    }

    /**
     * 充血保存或更新（主键存在则更新，否则插入；委托 {@link Repository#insertOrUpdate(Object)}）。
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
     * 充血条件更新（按查询条件更新）。
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
     * 充血聚合填充（从其他聚合补充数据）。
     */
    public <Q extends Query> void fill(Q query) {
        Repository repo = repository();
        if (Objects.nonNull(repo)) {
            ((Repository) repo).fill(query, this);
        }
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
     * 应用领域事件：反射派发到 {@code @EventHandler} 方法，并注册进未提交事件列表。
     *
     * <p>按事件运行时类型查找处理器；处理器映射由 {@link #AGGREGATE_HANDLER_CACHE}
     * 按聚合类做 {@link ClassValue} 缓存，子类处理器优先于超类同事件类型处理器。</p>
     *
     * <p>注意：本方法不做 {@code aggregateVersion} 连贯性校验 —— 版本校验属
     * EventStore 乐观锁职责（阶段 3），此处只负责事件应用。</p>
     *
     * <h3>用法</h3>
     * <pre>{@code
     * public void create(Money total) {
     *     apply(new OrderCreatedEvent(id(), total));
     * }
     *
     * &#64;EventHandler
     * private void on(OrderCreatedEvent event) {
     *     this.total = event.getTotal();
     * }
     * }</pre>
     *
     * @param event 要应用的事件
     * @param <E>   事件类型
     * @return 应用成功的事件
     * @throws NullPointerException  {@code event} 为 {@code null}
     * @throws IllegalStateException 找不到对应事件类型的 {@code @EventHandler} 方法，或反射调用失败
     */
    protected <E extends DomainEvent<?>> E apply(E event) {
        Objects.requireNonNull(event, "event must not be null");
        Method handler = AGGREGATE_HANDLER_CACHE.get(getClass()).get(event.getClass());
        if (Objects.isNull(handler)) {
            throw new IllegalStateException("No @EventHandler method found for event type: "
                    + event.getClass().getName() + " in aggregate: " + getClass().getName());
        }
        try {
            handler.setAccessible(true);
            handler.invoke(this, event);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke @EventHandler for "
                    + event.getClass().getName(), e);
        }
        mutableDomainEvents().add(event);
        return event;
    }

    /**
     * 从历史事件流重建聚合根（事件溯源回放）。
     *
     * <p>逐事件按 {@link #AGGREGATE_REPLAY_CACHE} 查找处理器并反射调用：</p>
     * <ul>
     *   <li>标有 {@link EventHandler#ignoreOnReplay()} 的处理器在回放时跳过</li>
     *   <li>本聚合不关心的历史事件类型静默跳过（允许跨聚合共享事件流）</li>
     * </ul>
     * <p>回放只重建状态、不进入未提交事件列表，结束后调用
     * {@link #clearDomainEvents()} 保证列表为空。</p>
     *
     * @param history 历史事件流；{@code null} 时直接返回
     * @throws IllegalStateException 反射调用处理器失败
     */
    public final void loadFromHistory(List<? extends DomainEvent<?>> history) {
        if (Objects.isNull(history)) {
            return;
        }
        Map<Class<?>, Method> handlers = AGGREGATE_REPLAY_CACHE.get(getClass());
        for (DomainEvent<?> event : history) {
            Method handler = handlers.get(event.getClass());
            if (Objects.isNull(handler)) {
                continue;
            }
            try {
                handler.setAccessible(true);
                handler.invoke(this, event);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to replay event "
                        + event.getClass().getName(), e);
            }
        }
        clearDomainEvents();
    }

    /** 处理器缓存（聚合类 → (事件类型 → {@code @EventHandler} Method）），apply 用。 */
    private static final ClassValue<Map<Class<?>, Method>> AGGREGATE_HANDLER_CACHE = new ClassValue<>() {
        @Override
        protected Map<Class<?>, Method> computeValue(Class<?> aggregateType) {
            Map<Class<?>, Method> map = new HashMap<>();
            scanHandlers(aggregateType, map, false);
            return map;
        }
    };

    /** 处理器缓存（聚合类 → (事件类型 → {@code @EventHandler} Method）），loadFromHistory 用（跳过 ignoreOnReplay）。 */
    private static final ClassValue<Map<Class<?>, Method>> AGGREGATE_REPLAY_CACHE = new ClassValue<>() {
        @Override
        protected Map<Class<?>, Method> computeValue(Class<?> aggregateType) {
            Map<Class<?>, Method> map = new HashMap<>();
            scanHandlers(aggregateType, map, true);
            return map;
        }
    };

    /**
     * 自聚合类向上遍历超类链，收集单 {@link DomainEvent} 参数且标有
     * {@code @EventHandler} 的方法；{@code putIfAbsent} 保证子类处理器优先。
     *
     * @param aggregateType 聚合根类
     * @param map           事件类型 → 处理器 Method 映射（原地填充）
     * @param skipIgnored   {@code true} 时跳过 {@link EventHandler#ignoreOnReplay()} 处理器
     */
    private static void scanHandlers(Class<?> aggregateType, Map<Class<?>, Method> map, boolean skipIgnored) {
        Class<?> current = aggregateType;
        while (Objects.nonNull(current) && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                EventHandler annotation = method.getAnnotation(EventHandler.class);
                if (Objects.isNull(annotation) || (skipIgnored && annotation.ignoreOnReplay())) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && DomainEvent.class.isAssignableFrom(parameters[0])) {
                    Class<?> eventType = parameters[0];
                    map.putIfAbsent(eventType, method);
                }
            }
            current = current.getSuperclass();
        }
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
    @SuppressWarnings("rawtypes")
    protected Repository repository() {
        return RepositoryRegistry.repository(this.getClass());
    }

}
