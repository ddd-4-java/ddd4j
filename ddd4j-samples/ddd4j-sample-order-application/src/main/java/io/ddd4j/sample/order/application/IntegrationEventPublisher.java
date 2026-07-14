package io.ddd4j.sample.order.application;

public interface IntegrationEventPublisher {
    void publish(OutboxMessage message);
}
