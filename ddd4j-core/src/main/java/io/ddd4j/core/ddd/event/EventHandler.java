package io.ddd4j.core.ddd.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记聚合根 / 实体内部的领域事件处理器方法。
 *
 * <p>ddd4j-core 的 {@link io.ddd4j.core.ddd.model.AggregateRoot#apply(DomainEvent)}
 * 通过反射调用所有标有此注解的方法，完成事件应用到聚合状态。
 *
 * <h3>使用</h3>
 * <pre>{@code
 * public class Order extends AggregateRoot<OrderId> {
 *     private Money total;
 *
 *     &#64;EventHandler
 *     public void on(OrderCreatedEvent event) {
 *         this.total = event.getTotal();
 *     }
 *
 *     public void pay(Money amount) {
 *         apply(new OrderPaidEvent(id, amount));
 *     }
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * 标记此处理器不参与历史事件回放（{@code loadFromHistory} 时跳过）。
     *
     * @return {@code true} 表示回放时跳过此处理器
     */
    boolean ignoreOnReplay() default false;
}
