package io.ddd4j.sample.quarkus.cqrs.event;

import io.ddd4j.sample.quarkus.cqrs.order.domain.event.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单领域事件监听器（Quarkus CDI {@code @Observes} 桥接）。
 *
 * <p>演示 ddd4j 业务事件与 Quarkus CDI 事件基础设施的桥接。
 * Order 业务方法触发 {@link OrderCreatedEvent} 等事件后，本观察者自动接收并记录日志。
 *
 * <p>Quarkus CDI 4.x 中 {@code @Observes} 默认是同步调用；
 * 如需异步可改成 {@code @ObservesAsync}（需要 {@code quarkus-vertx} 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class OrderEventObserver {

    /**
     * 监听订单创建事件（CDI 同步推送）。
     */
    void onOrderCreated(@Observes OrderCreatedEvent event) {
        log.info("[OrderEventObserver] 订单已创建: orderId={}", event.source());
    }

    /**
     * 监听订单行添加事件。
     */
    void onOrderLineAdded(@Observes OrderLineAddedEvent event) {
        log.info("[OrderEventObserver] 订单行已添加: orderId={}", event.source());
    }

    /**
     * 监听订单支付事件。
     */
    void onOrderPaid(@Observes OrderPaidEvent event) {
        log.info("[OrderEventObserver] 订单已支付: orderId={}", event.source());
    }

    /**
     * 监听订单发货事件。
     */
    void onOrderShipped(@Observes OrderShippedEvent event) {
        log.info("[OrderEventObserver] 订单已发货: orderId={}", event.source());
    }

    /**
     * 监听订单取消事件。
     */
    void onOrderCancelled(@Observes OrderCancelledEvent event) {
        log.info("[OrderEventObserver] 订单已取消: orderId={}", event.source());
    }
}