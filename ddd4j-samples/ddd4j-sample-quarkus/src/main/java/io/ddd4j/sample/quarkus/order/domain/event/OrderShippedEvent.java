package io.ddd4j.sample.quarkus.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单已发货事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderShippedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderShippedEvent(String orderId) {
        super(orderId);
    }
}