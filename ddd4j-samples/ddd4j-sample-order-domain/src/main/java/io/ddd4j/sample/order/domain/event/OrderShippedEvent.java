package io.ddd4j.sample.order.domain.event;

public final class OrderShippedEvent extends OrderDomainEvent {
    public OrderShippedEvent(String orderId) {
        super(orderId);
    }
}
