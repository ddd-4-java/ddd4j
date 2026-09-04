package io.ddd4j.sample.order.domain.event;

public final class OrderCancelledEvent extends OrderDomainEvent {
    public OrderCancelledEvent(String orderId) {
        super(orderId);
    }
}
