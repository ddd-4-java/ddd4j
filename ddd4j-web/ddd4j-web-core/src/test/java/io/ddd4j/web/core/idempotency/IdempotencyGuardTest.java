package io.ddd4j.web.core.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyGuardTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private static final class RecordingGuard implements IdempotencyGuard {

        private final boolean acquired;
        private String completedKey;
        private String releasedKey;

        private RecordingGuard(boolean acquired) {
            this.acquired = acquired;
        }

        @Override
        public boolean acquire(String key, Duration ttl) {
            return acquired;
        }

        @Override
        public void complete(String key) {
            this.completedKey = key;
        }

        @Override
        public void release(String key) {
            this.releasedKey = key;
        }
    }

    @Test
    void acquireLeaseReturnsLeaseWhenAcquired() {
        RecordingGuard guard = new RecordingGuard(true);

        Optional<IdempotencyLease> lease = guard.acquireLease("order-1", TTL);

        assertTrue(lease.isPresent());
        assertEquals("order-1", lease.get().key());
        assertNull(lease.get().ownerToken());
        assertEquals(TTL, lease.get().ttl());
    }

    @Test
    void acquireLeaseReturnsEmptyWhenNotAcquired() {
        RecordingGuard guard = new RecordingGuard(false);
        assertFalse(guard.acquireLease("order-1", TTL).isPresent());
    }

    @Test
    void completeByLeaseDelegatesToKey() {
        RecordingGuard guard = new RecordingGuard(true);
        IdempotencyLease lease = guard.acquireLease("order-1", TTL).get();

        guard.complete(lease);

        assertEquals("order-1", guard.completedKey);
    }

    @Test
    void releaseByLeaseDelegatesToKey() {
        RecordingGuard guard = new RecordingGuard(true);
        IdempotencyLease lease = guard.acquireLease("order-1", TTL).get();

        guard.release(lease);

        assertEquals("order-1", guard.releasedKey);
    }
}
