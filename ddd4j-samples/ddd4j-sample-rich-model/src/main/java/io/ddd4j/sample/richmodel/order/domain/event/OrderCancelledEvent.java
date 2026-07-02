package io.ddd4j.sample.richmodel.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * Raised when an order is cancelled.
 */
public class OrderCancelledEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderCancelledEvent(String orderId) {
        super(orderId);
    }
}
