package io.ddd4j.sample.order.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResilientIdempotencyPortTest {

    @Test
    void shouldUseFallbackWhenPrimaryStorageIsUnavailable() {
        RecordingIdempotencyPort fallback = new RecordingIdempotencyPort();
        IdempotencyPort resilient = new ResilientIdempotencyPort(new FailingIdempotencyPort(), fallback);

        assertTrue(resilient.acquire("payment-1", Duration.ofMinutes(1)));
        resilient.complete("payment-1", "order-1");
        resilient.release("payment-1");

        assertTrue(fallback.acquired.get());
        assertTrue(fallback.completed.get());
        assertTrue(fallback.released.get());
    }

    @Test
    void shouldKeepPrimaryResultWhenPrimaryStorageIsHealthy() {
        RecordingIdempotencyPort primary = new RecordingIdempotencyPort();
        RecordingIdempotencyPort fallback = new RecordingIdempotencyPort();
        IdempotencyPort resilient = new ResilientIdempotencyPort(primary, fallback);

        assertTrue(resilient.acquire("payment-2", Duration.ofMinutes(1)));

        assertTrue(primary.acquired.get());
        assertFalse(fallback.acquired.get());
    }

    @Test
    void shouldKeepOutboxMessagePendingWhenNoTransportIsAvailable() {
        UnavailableIntegrationEventPublisher publisher = new UnavailableIntegrationEventPublisher("Kafka is disabled");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> publisher.publish(new OutboxMessage("event-1", "order-1", "OrderPaid", null, Instant.now())));

        assertTrue(exception.getMessage().contains("Kafka is disabled"));
    }

    private static final class FailingIdempotencyPort implements IdempotencyPort {

        @Override
        public boolean acquire(String key, Duration ttl) {
            throw new IllegalStateException("Redis is unavailable");
        }

        @Override
        public void complete(String key, Object result) {
            throw new IllegalStateException("Redis is unavailable");
        }

        @Override
        public void release(String key) {
            throw new IllegalStateException("Redis is unavailable");
        }
    }

    private static final class RecordingIdempotencyPort implements IdempotencyPort {

        private final AtomicBoolean acquired = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();

        @Override
        public boolean acquire(String key, Duration ttl) {
            acquired.set(true);
            return true;
        }

        @Override
        public void complete(String key, Object result) {
            completed.set(true);
        }

        @Override
        public void release(String key) {
            released.set(true);
        }
    }
}
