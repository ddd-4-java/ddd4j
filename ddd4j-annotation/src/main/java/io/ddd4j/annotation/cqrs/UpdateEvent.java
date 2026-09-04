package io.ddd4j.annotation.cqrs;

import io.ddd4j.annotation.Contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CQRS 读侧"更新"事件处理器。
 *
 * <p>标注于 CQRS 读侧投影的方法，标识该方法处理"实体被更新"类型的事件。
 *
 * <p>典型用法：
 * <pre>{@code
 * public class OrderListView extends DddJpaView {
 *     @UpdateEvent(OrderUpdatedEvent.class)
 *     public void onOrderUpdated(EntityManager em, OrderUpdatedEvent event) {
 *         OrderListEntry entry = em.find(OrderListEntry.class, event.getEntityId());
 *         entry.updateFrom(event);
 *     }
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Contract
public @interface UpdateEvent {

    /**
     * 要处理的事件类型。
     */
    Class<?> value();
}
