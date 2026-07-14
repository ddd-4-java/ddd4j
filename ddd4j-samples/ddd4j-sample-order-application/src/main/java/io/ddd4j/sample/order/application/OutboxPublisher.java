package io.ddd4j.sample.order.application;

import java.util.Objects;

public final class OutboxPublisher {

    private final OutboxPort outbox;
    private final IntegrationEventPublisher publisher;

    public OutboxPublisher(OutboxPort outbox, IntegrationEventPublisher publisher) {
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    public int publishPending(int limit) {
        int published = 0;
        for (OutboxMessage message : outbox.pending(limit)) {
            try {
                publisher.publish(message);
                outbox.markPublished(message.id());
                published++;
            } catch (RuntimeException exception) {
                outbox.markFailed(message.id(), exception.getMessage());
            }
        }
        return published;
    }
}
