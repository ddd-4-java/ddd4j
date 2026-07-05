package io.ddd4j.sample.javalin.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单取消事件。
 *
 * <p>当订单被取消（{@code Order.cancel()}）时触发。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderCancelledEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderCancelledEvent(String orderId) {
        super(orderId);
    }
}
