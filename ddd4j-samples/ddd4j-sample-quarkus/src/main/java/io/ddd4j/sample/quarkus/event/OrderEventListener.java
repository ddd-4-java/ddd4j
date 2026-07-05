package io.ddd4j.sample.quarkus.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.sample.quarkus.order.domain.event.OrderCancelledEvent;
import io.ddd4j.sample.quarkus.order.domain.event.OrderCreatedEvent;
import io.ddd4j.sample.quarkus.order.domain.event.OrderPaidEvent;
import io.ddd4j.sample.quarkus.order.domain.event.OrderShippedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

/**
 * 领域事件监听器：演示 Quarkus CDI {@code @Observes} 监听 ddd4j 领域事件。
 *
 * <p>ddd4j 的 {@link DomainEvent} 通过 {@code DomainEventPublisher} SPI 发布后，
 * Quarkus CDI 的事件机制可通过 {@code @Observes} 同步接收。
 *
 * <p>本监听器在每个订单状态变更时打印日志，真实项目可替换为：
 * <ul>
 *   <li>发送通知（短信/邮件/站内信）</li>
 *   <li>触发下游流程（库存扣减/物流调度）</li>
 *   <li>记录审计日志</li>
 * </ul>
 *
 * <h3>与 Spring 版对照</h3>
 * <p>Spring 使用 {@code @EventListener} 或 {@code ApplicationEventPublisher}；
 * Quarkus 使用 {@code @Observes}，业务逻辑完全相同，仅注解不同。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class OrderEventListener {

    /**
     * 监听订单创建事件。
     *
     * @param event 订单创建事件
     */
    void onOrderCreated(@Observes OrderCreatedEvent event) {
        log.info("[OrderEventListener] 订单已创建: orderId={}", event.source());
    }

    /**
     * 监听订单支付事件。
     *
     * @param event 订单支付事件
     */
    void onOrderPaid(@Observes OrderPaidEvent event) {
        log.info("[OrderEventListener] 订单已支付: orderId={}, 可触发货款确认流程", event.source());
    }

    /**
     * 监听订单发货事件。
     *
     * @param event 订单发货事件
     */
    void onOrderShipped(@Observes OrderShippedEvent event) {
        log.info("[OrderEventListener] 订单已发货: orderId={}, 可触发物流跟踪", event.source());
    }

    /**
     * 监听订单取消事件。
     *
     * @param event 订单取消事件
     */
    void onOrderCancelled(@Observes OrderCancelledEvent event) {
        log.info("[OrderEventListener] 订单已取消: orderId={}, 可触发退款流程", event.source());
    }

    /**
     * 监听所有领域事件（兜底处理器）。
     *
     * <p>使用 {@code @Observes} 监听泛型 {@link DomainEvent}，
     * 演示 CDI 事件的多态分发能力。
     *
     * @param event 任意领域事件
     */
    void onAnyDomainEvent(@Observes DomainEvent<?> event) {
        log.debug("[OrderEventListener] 领域事件: type={}, source={}",
                event.getClass().getSimpleName(), event.source());
    }
}
