package io.ddd4j.sample.richmodel.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * Raised when an order is created.
 */
public class OrderCreatedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderCreatedEvent(String orderId) {
        super(orderId);
    }
}
