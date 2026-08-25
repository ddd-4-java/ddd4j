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
package io.ddd4j.core.ddd.model;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.event.DomainEvent;
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

    /**
     * 事件处理器方法缓存（ClassValue 二级索引）。
     * 外层 key = 聚合根 Class，内层 key = 事件 Class → 对应的 onXxx 方法（可能为 null）。
     */
    private static final ClassValue<ClassValue<Method>> EVENT_HANDLER_CACHE = new ClassValue<>() {
        @Override
        protected ClassValue<Method> computeValue(Class<?> aggregateClass) {
            return new ClassValue<>() {
                @Override
                protected Method computeValue(Class<?> eventClass) {
                    String handlerName = "on" + eventClass.getSimpleName();
                    try {
                        Method m = aggregateClass.getDeclaredMethod(handlerName, eventClass);
                        m.setAccessible(true);
                        return m;
                    } catch (NoSuchMethodException e) {
                        return null;
                    }
                }
            };
        }
    };

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

    // ========================= 事件溯源 =========================

    /**
     * 应用领域事件：通过 ClassValue 缓存的反射路由到聚合内部的 {@code on<EventTypeSimpleName>} 方法。
     *
     * <p>例如 {@code OrderCreatedEvent} 路由到 {@code onOrderCreated(OrderCreatedEvent event)}。
     * 若聚合未定义对应的 handler 方法，则静默忽略（不影响聚合状态）。
     *
     * <p>注意：{@code apply} 仅用于事件溯源回放（从历史事件重建聚合状态），
     * 不会将事件注册到未提交事件缓冲区。业务方法应使用 {@link #registerEvent(DomainEvent)}。
     *
     * @param event 领域事件
     * @param <E>   事件类型
     */
    protected <E extends DomainEvent<?>> void apply(E event) {
        Objects.requireNonNull(event, "event must not be null");
        ClassValue<Method> handlerCache = EVENT_HANDLER_CACHE.get(this.getClass());
        Method handler = handlerCache.get(event.getClass());
        if (Objects.nonNull(handler)) {
            try {
                handler.invoke(this, event);
            } catch (Exception e) {
                throw new BizRuntimeException("Failed to apply event {} on aggregate {}",
                        event.getClass().getSimpleName(), this.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 从历史事件列表重建聚合状态（事件溯源核心方法）。
     *
     * <p>按顺序依次调用 {@link #apply(DomainEvent)}，使聚合状态恢复到最新版本。
     * 典型用法：从 EventStore 读取事件后，调用此方法重建聚合根。
     *
     * <pre>{@code
     * Order order = new Order(); // 空聚合
     * List<DomainEvent<?>> history = eventStore.read(orderId).stream()
     *         .map(StoredEvent::event)
     *         .map(e -> (DomainEvent<?>) e)
     *         .toList();
     * order.loadFromHistory(history);
     * }</pre>
     *
     * @param events 历史事件列表（按版本升序）
     */
    public void loadFromHistory(List<? extends DomainEvent<?>> events) {
        if (Objects.isNull(events) || events.isEmpty()) {
            return;
        }
        for (DomainEvent<?> event : events) {
            apply(event);
        }
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
