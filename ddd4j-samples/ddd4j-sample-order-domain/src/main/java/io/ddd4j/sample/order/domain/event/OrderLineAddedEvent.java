package io.ddd4j.sample.order.domain.event;

public final class OrderLineAddedEvent extends OrderDomainEvent {
    public OrderLineAddedEvent(String orderId) {
        super(orderId);
    }
}
