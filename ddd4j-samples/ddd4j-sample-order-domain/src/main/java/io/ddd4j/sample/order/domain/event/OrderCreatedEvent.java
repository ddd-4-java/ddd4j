package io.ddd4j.sample.order.domain.event;

public final class OrderCreatedEvent extends OrderDomainEvent {
    public OrderCreatedEvent(String orderId) {
        super(orderId);
    }
}
