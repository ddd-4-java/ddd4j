package io.ddd4j.sample.order.domain.event;

public final class OrderPaidEvent extends OrderDomainEvent {
    public OrderPaidEvent(String orderId) {
        super(orderId);
    }
}
