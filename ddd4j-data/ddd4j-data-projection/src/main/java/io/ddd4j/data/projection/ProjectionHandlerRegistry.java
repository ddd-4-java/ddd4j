package io.ddd4j.data.projection;

import io.ddd4j.core.ddd.event.DomainEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投影处理器注册中心（框架无关）。
 * <p>
 * 由各运行时适配器（阶段 7 Task 7.7+：Spring／Quarkus／Micronaut／Helidon／
 * Javalin／Vertx／Dropwizard）在装配期收集业务 {@link ProjectionHandler} bean，
 * 逐个注册到本类；随后调度器通过 {@link #findHandler(Class)} 定位订阅者，
 * 交给 {@link ProjectionDispatcher} 拉块分发。本类只维护「事件类型 → handler」
 * 映射，不执行事件、不感知任何运行时框架。
 *
 * <h3>注册语义（与 CommandRegistry 同源）</h3>
 * <p>
 * {@link #register} 采用 <b>先校验全部订阅类型无冲突、再统一落库</b> 的整批拒绝
 * （all-or-nothing）语义：任一事件类型已被其他 handler 注册即抛
 * {@link IllegalStateException}（消息含事件类型全限定名），且本 handler 声明的
 * 所有类型（包括无冲突者）均不落库——多类型 handler 中途冲突不会留下
 * 「前几个类型已落库」的半注册状态。该语义与 ddd4j-data-cqrs 的
 * {@code CommandRegistry}（ADR-0004 对 fuin 先例的保留决策）同源：
 * 冲突在装配期暴露，注册中心保持长生命周期共享组件的一致性。
 *
 * <h3>线程模型</h3>
 * <p>
 * 注册发生在装配期（单线程或已外部同步）；内部使用 {@link ConcurrentHashMap}
 * 保证 {@link #findHandler} 无锁读。并发的重复注册属契约外用法，落库阶段的
 * {@code putIfAbsent} 仅作防御性兜底。
 *
 * @author <a href="https://github.com/partme-ai">Partme.AI</a>
 * @see ProjectionHandler
 * @see ProjectionDispatcher
 * @since 2.0.x
 */
public class ProjectionHandlerRegistry {

    private final Map<Class<?>, ProjectionHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 注册一个投影处理器（整批拒绝语义）。
     * <p>
     * 先校验 {@link ProjectionHandler#eventTypes()} 声明的全部事件类型均无冲突，
     * 再统一写入；任一类型已被其他 handler 注册即抛 {@link IllegalStateException}，
     * 且本 handler 声明的所有类型都不落库（与 {@code CommandRegistry} 同源语义）。
     *
     * @param handler 投影处理器，非空
     * @throws NullPointerException  handler 为 null
     * @throws IllegalStateException 任一事件类型已被注册（消息含事件类型全限定名）
     */
    public void register(ProjectionHandler handler) {
        ProjectionHandler actual = Objects.requireNonNull(handler, "handler must not be null");
        Set<Class<?>> eventTypes = Set.copyOf(actual.eventTypes());
        for (Class<?> eventType : eventTypes) {
            if (handlers.containsKey(eventType)) {
                throw new IllegalStateException(
                        "Duplicate projection handler registration for event: " + eventType.getName());
            }
        }
        for (Class<?> eventType : eventTypes) {
            ProjectionHandler previous = handlers.putIfAbsent(eventType, actual);
            if (Objects.nonNull(previous)) {
                throw new IllegalStateException(
                        "Duplicate projection handler registration for event: " + eventType.getName());
            }
        }
    }

    /**
     * 返回已注册 handler 的不可变视图（声明多类型的 handler 只出现一次）。
     * <p>
     * 内部映射按「事件类型」键控（多类型 handler 对应多个键值），故以去重集合返回；
     * 供调度器遍历注册面（按 {@link ProjectionHandler#getCron()} 注册周期任务）。
     *
     * @return 已注册 handler 的不可变集合
     */
    public Collection<ProjectionHandler> all() {
        return Collections.unmodifiableCollection(new LinkedHashSet<>(handlers.values()));
    }

    /**
     * 按事件类型查找订阅的 handler。
     *
     * @param eventType 事件类型，非空
     * @param <E>       事件类型泛型
     * @return 订阅该类型的 handler；未注册返回 {@link Optional#empty()}
     *         （兜底策略由 {@link ProjectionDispatcher} 决定）
     * @throws NullPointerException eventType 为 null
     */
    public <E extends DomainEvent<?>> Optional<ProjectionHandler> findHandler(Class<E> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        return Optional.ofNullable(handlers.get(eventType));
    }
}
