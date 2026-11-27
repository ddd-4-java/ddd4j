package io.ddd4j.annotation.cqrs;

import io.ddd4j.annotation.Contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CQRS 读侧"删除"事件处理器。
 *
 * <p>标注于 CQRS 读侧投影的方法，标识该方法处理"实体被删除"类型的事件。
 *
 * <p>典型用法：
 * <pre>{@code
 * public class OrderListView extends DddJpaView {
 *     @DeleteEvent(OrderDeletedEvent.class)
 *     public void onOrderDeleted(EntityManager em, OrderDeletedEvent event) {
 *         OrderListEntry entry = em.find(OrderListEntry.class, event.getEntityId());
 *         if (Objects.nonNull(entry)) {
 *             em.remove(entry);
 *         }
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
public @interface DeleteEvent {

    /**
     * \u8981\u5904\u7406\u7684\u4e8b\u4ef6\u7c7b\u578b\u3002
     *
     * @return \u8981\u5904\u7406\u7684\u4e8b\u4ef6\u7c7b\u578b
     */
    Class<?> value();
}
