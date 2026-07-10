package io.ddd4j.sample.spring.event;

import io.ddd4j.sample.spring.order.domain.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 订单领域事件监听器。
 *
 * <p>演示如何通过 Spring 原生 {@link EventListener} 监听 ddd4j 的 {@code DomainEvent}。
 * 当 ddd4j-core 的 {@code DomainEvent.publish()} 被调用时：
 * <ol>
 *   <li>ddd4j 框架根据 SPI 注册表找到 {@code SpringDomainEventPublisher}</li>
 *   <li>{@code SpringDomainEventPublisher} 使用 Spring {@code ApplicationEventPublisher} 发布事件</li>
 *   <li>Spring 容器通过 {@link EventListener} 方法调度到本监听器</li>
 * </ol>
 *
 * <p>这种桥接让业务方可以保持 ddd4j DomainEvent 的零框架耦合语义，
 * 同时享受 Spring 事件基础设施（异步 / 事务同步 / SpEL 条件）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Component
public class OrderEventListener {

    /**
     * 监听订单创建事件。
     *
     * @param event 订单创建事件
     */
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[Domain Event] Order created: orderId={}", event.source());
    }

    /**
     * 监听订单行添加事件。
     *
     * @param event 订单行添加事件
     */
    @EventListener
    public void onOrderLineAdded(OrderLineAddedEvent event) {
        log.info("[Domain Event] Order line added: orderId={}", event.source());
    }

    /**
     * 监听订单支付事件。
     *
     * @param event 订单支付事件
     */
    @EventListener
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("[Domain Event] Order paid: orderId={}", event.source());
    }

    /**
     * 监听订单发货事件。
     *
     * @param event 订单发货事件
     */
    @EventListener
    public void onOrderShipped(OrderShippedEvent event) {
        log.info("[Domain Event] Order shipped: orderId={}", event.source());
    }

    /**
     * 监听订单取消事件。
     *
     * @param event 订单取消事件
     */
    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("[Domain Event] Order cancelled: orderId={}", event.source());
    }
}
