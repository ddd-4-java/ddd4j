package io.ddd4j.annotation.cqrs;

import io.ddd4j.annotation.Contract;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CQRS 读侧"创建"事件处理器。
 *
 * <p>标注于 {@link io.ddd4j.core.cqrs.query.DddView} 子类的方法，标识该方法处理"实体被创建"类型的事件。
 *
 * <p>典型用法：
 * <pre>{@code
 * public class OrderListView extends DddJpaView {
 *     @CreateEvent(OrderCreatedEvent.class)
 *     public void onOrderCreated(EntityManager em, OrderCreatedEvent event) {
 *         em.persist(OrderListEntry.from(event));
 *     }
 * }
 * }</pre>
 *
 * <p>由各框架适配层（{@code ddd4j-spring} / {@code ddd4j-quarkus}）的
 * {@code SpringEventHandlerRegistry} / {@code QuarkusEventHandlerRegistry} 在运行时反射装配到
 * {@link io.ddd4j.core.cqrs.event.DddEventDispatcher} 路由表。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Contract
public @interface CreateEvent {

    /**
     * 事件类型（{@link org.fuin.ddd4j.core.Event} 子类）。
     */
    Class<?> value();
}
