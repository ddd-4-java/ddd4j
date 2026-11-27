package io.ddd4j.web.core.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * \u7c7b\u578b\u8bf4\u660e\u3002
 * @author ddd4j
 * @since 1.0.x
 */
class IdempotencyGuardTest {

    /** \u6d4b\u8bd5\u7f13\u5b58\u6216\u79df\u7ea6\u4f7f\u7528\u7684\u4fdd\u7559\u65f6\u957f\u3002 */
    private static final Duration TTL = Duration.ofMinutes(5);

    /** \u8bb0\u5f55\u5b8c\u6210\u4e0e\u91ca\u653e\u8c03\u7528\u7684\u6869\u5b9e\u73b0\uff0c\u4fbf\u4e8e\u65ad\u8a00\u79df\u7ea6\u5230\u6309\u952e\u64cd\u4f5c\u7684\u59d4\u6258\u884c\u4e3a\u3002 */
    private static final class RecordingGuard implements IdempotencyGuard {

        /** \u8bb0\u5f55\u53d7\u6d4b\u64cd\u4f5c\u662f\u5426\u6210\u529f\u83b7\u53d6\u4e86\u5f53\u524d\u8d44\u6e90\u3002 */
        private final boolean acquired;
        /** \u6700\u8fd1\u4e00\u4e2a\u5b8c\u6210\u5904\u7406\u7684\u4e1a\u52a1\u6216\u5e42\u7b49\u8bb0\u5f55\u952e\u3002 */
        private String completedKey;

        /**
         * \u8bbe\u7f6e <code>acquired</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>RecordingGuard</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param acquired \u7528\u4e8e\u521d\u59cb\u5316 <code>RecordingGuard</code> \u7684 <code>boolean</code> \u503c
         */
        private RecordingGuard(boolean acquired) {
            this.acquired = acquired;
        }

        /**
         * acquire() \u65b9\u6cd5\u3002
         * @param key \u5e42\u7b49\u952e\u3002
         * @param ttl \u9501\u7684\u6709\u6548\u671f\u3002
         * @return \u5224\u65ad\u7ed3\u679c\u3002
         */
        @Override
        public boolean acquire(String key, Duration ttl) {
            return acquired;
        }

        /**
         * complete() \u65b9\u6cd5\u3002
         * @param key \u5e42\u7b49\u952e\u3002
         */
        @Override
        public void complete(String key) {
            this.completedKey = key;
        }

        /**
         * release() \u65b9\u6cd5\u3002
         * @param key \u5e42\u7b49\u952e\u3002
         */
        @Override
        public void release(String key) {
        }
    }

    /** \u9a8c\u8bc1<code>lease.isPresent()</code>\u4e3a\u771f\uff0c<code>lease.get().key()</code> \u7b49\u4e8e <code>"order-1"</code>\uff0c<code>lease.get().ttl()</code> \u7b49\u4e8e <code>TTL</code>\u3002 */
    @Test
    void acquireLeaseReturnsLeaseWhenAcquired() {
        RecordingGuard guard = new RecordingGuard(true);

        Optional<IdempotencyLease> lease = guard.acquireLease("order-1", TTL);

        assertTrue(lease.isPresent());
        assertEquals("order-1", lease.get().key());
        assertNull(lease.get().ownerToken());
        assertEquals(TTL, lease.get().ttl());
    }

    /** \u9a8c\u8bc1<code>guard.acquireLease("order-1", TTL).isPresent()</code>\u4e3a\u5047\u3002 */
    @Test
    void acquireLeaseReturnsEmptyWhenNotAcquired() {
        RecordingGuard guard = new RecordingGuard(false);
        assertFalse(guard.acquireLease("order-1", TTL).isPresent());
    }

    /** \u9a8c\u8bc1<code>guard.completedKey</code> \u7b49\u4e8e <code>"order-1"</code>\u3002 */
    @Test
    void completeByLeaseDelegatesToKey() {
        RecordingGuard guard = new RecordingGuard(true);
        IdempotencyLease lease = guard.acquireLease("order-1", TTL).orElseThrow();

        guard.complete(lease);

        assertEquals("order-1", guard.completedKey);
    }
}
