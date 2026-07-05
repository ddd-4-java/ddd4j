package io.ddd4j.sample.quarkus.shiro.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单取消事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderCancelledEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderCancelledEvent(String orderId) {
        super(orderId);
    }
}