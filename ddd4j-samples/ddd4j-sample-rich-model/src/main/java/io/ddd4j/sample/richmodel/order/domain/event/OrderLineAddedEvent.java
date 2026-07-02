package io.ddd4j.sample.richmodel.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * Raised when an order line is added.
 */
public class OrderLineAddedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderLineAddedEvent(String orderId) {
        super(orderId);
    }
}
