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
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 充血聚合根基类（ddd4j 唯一推荐）。
 *
 * <p>本类支持两种使用模式，业务方应根据持久化策略选择其一，
 * <b>不应在同一聚合中混用</b>（见下方警告）。
 *
 * <h2>模式一：Active Record（充血 CRUD）</h2>
 * <p>聚合根直接持有 {@code save()/update()/delete()} 等实例方法，
 * 通过 {@link RepositoryRegistry} 获取仓储实例完成持久化。
 * 适用于传统 CRUD 场景，状态直接落库。</p>
 * <pre>{@code
 * // 实例方法
 * Order order = new Order(orderId, total);
 * order.save();         // ← OrderRepository.save(order)
 * order.pay(amount);
 * order.update();       // ← OrderRepository.updateById(order)
 * order.saveOrUpdate(); // ← OrderRepository.insertOrUpdate(order)
 * order.delete();       // ← OrderRepository.delete(order)
 *
 * // 静态批量
 * AggregateRoot.save(orders);   // ← OrderRepository.saveAll(orders)
 * AggregateRoot.delete(query);  // ← OrderRepository.deleteByQuery(query)
 *
 * // 静态查询
 * Optional<Order> found = AggregateRoot.get(Order.class, orderId);
 * List<Order> all = AggregateRoot.list(Order.class);
 * Page<Order> page = AggregateRoot.page(query);
 * }</pre>
 *
 * <h2>模式二：Event Sourcing（事件溯源）</h2>
 * <p>聚合根状态完全由领域事件驱动。业务方法通过 {@link #registerEvent(DomainEvent)} 注册事件，
 * 仓储层持久化事件流而非聚合快照。重建状态时使用 {@link #loadFromHistory(List)}。</p>
 * <pre>{@code
 * public class Order extends AggregateRoot<OrderId> {
 *     private Money total;
 *     private OrderStatus status;
 *
 *     public void pay(Money amount) {
 *         // 业务校验 ...
 *         registerEvent(new OrderPaidEvent(id, amount));
 *     }
 *
 *     // 事件溯源 handler（方法名 = on + 事件类简单名）
 *     void onOrderPaid(OrderPaidEvent event) {
 *         this.status = OrderStatus.PAID;
 *     }
 * }
 *
 * // 重建聚合状态
 * Order order = new Order();
 * List<DomainEvent<?>> history = eventStore.read(orderId);
 * order.loadFromHistory(history);
 *
 * // 获取未提交事件
 * List<DomainEvent<?>> pending = order.pullDomainEvents();
 * }</pre>
 *
 * <h2>警告：两种模式不应混用</h2>
 * <p><b>在同一聚合中同时使用 Active Record 方法（{@code save()/update()}）
 * 和 Event Sourcing 方法（{@code registerEvent()/pullDomainEvents()}）会导致：</b></p>
 * <ul>
 *   <li>事件丢失 —— {@code save()} 直接落库快照，未提交的注册事件被丢弃</li>
 *   <li>重复持久化 —— 事件已通过 EventStore 持久化，再次调用 {@code save()} 造成快照冗余写入</li>
 *   <li>状态不一致 —— 快照与事件流两条轨道产生分叉，重建结果不可预期</li>
 * </ul>
 * <p>选择一种模式并贯穿整个聚合的生命周期。</p>
 *
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class AggregateRoot<ID extends Serializable> implements Entity<ID> {

    /**
     * 事件处理器方法缓存（ClassValue 二级索引）。
     * 外层 key = 聚合根 Class，内层 key = 事件 Class → 处理器 Method（可能为 null）。
     * 解析优先级：{@code @EventHandler} 注解方法 > {@code on<EventType>} 命名约定（3.0.x 兼容）。
     */
    private static final ClassValue<ClassValue<Method>> EVENT_HANDLER_CACHE = new ClassValue<ClassValue<Method>>() {
        @Override
        protected ClassValue<Method> computeValue(Class<?> aggregateClass) {
            return new ClassValue<Method>() {
                @Override
                protected Method computeValue(Class<?> eventClass) {
                    return resolveHandler(aggregateClass, eventClass);
                }
            };
        }
    };

    /**
     * 解析事件处理器：优先 {@code @EventHandler} 注解方法（沿继承链，参数可接收该事件类型），
     * 回退到 {@code on<EventType>} 命名约定（3.0.x 兼容路径）。
     *
     * @param aggregateClass 聚合根类型
     * @param eventClass     事件类型
     * @return 处理器方法；两者均未命中时返回 {@code null}
     */
    private static Method resolveHandler(Class<?> aggregateClass, Class<?> eventClass) {
        for (Class<?> current = aggregateClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EventHandler.class)) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(eventClass)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        String handlerName = "on" + eventClass.getSimpleName();
        try {
            Method method = aggregateClass.getDeclaredMethod(handlerName, eventClass);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

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
     * <h3>异常处理</h3>
     * <p>反射调用精确捕获两类异常：
     * <ul>
     *   <li>{@link InvocationTargetException} — handler 自身抛出的异常解包后透传，
     *       运行时异常直接抛出，受检异常包装为 {@link BizRuntimeException}</li>
     *   <li>{@link IllegalAccessException} — 通常为 JDK 17+ 模块系统未开放反射访问，
     *       错误消息明确指引解决方案（{@code --add-opens} 或 {@code module-info.java opens}）</li>
     * </ul>
     *
     * @param event 领域事件
     * @param <E>   事件类型
     */
    /**
     * 应用领域事件（2.0.x 语义）。
     *
     * <p>反射派发到事件处理器（{@code @EventHandler} 优先，{@code on<Type>} 回退），
     * 并返回事件本身。找不到处理器时抛 {@link IllegalStateException}。
     *
     * @param event 领域事件
     * @return 传入的事件（链式调用便利）
     * @throws IllegalStateException 找不到对应事件类型的处理器，或反射调用失败
     */
    protected <E extends DomainEvent<?>> E apply(E event) {
        return apply(event, false);
    }

    /**
     * 事件应用内部实现。
     *
     * <p>反射派发到事件处理器（{@code @EventHandler} 优先，{@code on<Type>} 回退），
     * 并注册进未提交事件列表（2.0.x 语义：找不到处理器时抛 {@link IllegalStateException}）。
     * 回放模式（{@code replay = true}）下跳过标有 {@code ignoreOnReplay = true} 的处理器。
     *
     * @param event 领域事件
     * @param replay 是否处于历史回放（{@code loadFromHistory}）
     * @return 传入的事件
     * @throws IllegalStateException 找不到对应事件类型的处理器，或反射调用失败
     */
    private <E extends DomainEvent<?>> E apply(E event, boolean replay) {
        Objects.requireNonNull(event, "event must not be null");
        ClassValue<Method> handlerCache = EVENT_HANDLER_CACHE.get(this.getClass());
        Method handler = handlerCache.get(event.getClass());
        if (Objects.isNull(handler)) {
            throw new IllegalStateException("No @EventHandler method found for event type: "
                    + event.getClass().getName() + " on aggregate " + this.getClass().getName());
        }
        if (replay && handler.isAnnotationPresent(EventHandler.class)
                && handler.getAnnotation(EventHandler.class).ignoreOnReplay()) {
            return event;
        }
        try {
            handler.invoke(this, event);
            if (!replay) {
                // 2.0.x 语义：apply 反射派发后注册进未提交事件列表；回放（loadFromHistory）不入队
                mutableDomainEvents().add(event);
            }
        } catch (InvocationTargetException e) {
            // handler 自身抛出的业务异常：解包透传，避免包装后丢失原始堆栈
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                RuntimeException re = (RuntimeException) cause;
                throw re;
            }
            if (cause instanceof Error) {
                Error err = (Error) cause;
                throw err;
            }
            throw new BizRuntimeException("Failed to apply event " + event.getClass().getSimpleName()
                    + " on aggregate " + this.getClass().getSimpleName(), cause);
        } catch (IllegalAccessException e) {
            // JDK 17+ 模块系统限制：handler 所在包未对反射开放
            throw new BizRuntimeException(
                    "Cannot access @EventHandler method '" + handler.getName()
                            + "' on aggregate " + this.getClass().getName()
                            + ". On JDK 17+, ensure the handler's declaring package is opened via"
                            + " 'opens' in module-info.java or '--add-opens' JVM flag.",
                    e);
        } catch (Exception e) {
            // 其他反射异常（如 setAccessible 抛出的 InaccessibleObjectException）
            throw new BizRuntimeException("Failed to apply event " + event.getClass().getSimpleName()
                    + " on aggregate " + this.getClass().getSimpleName(), e);
        }
        return event;
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
            apply(event, true);
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
