package io.ddd4j.sample.richmodel.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * Raised when an order is paid.
 */
public class OrderPaidEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderPaidEvent(String orderId) {
        super(orderId);
    }
}
