package io.ddd4j.sample.richmodel.order.domain.event;

import io.ddd4j.core.domain.event.DomainEvent;

/**
 * Raised when an order is shipped.
 */
public class OrderShippedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderShippedEvent(String orderId) {
        super(orderId);
    }
}
