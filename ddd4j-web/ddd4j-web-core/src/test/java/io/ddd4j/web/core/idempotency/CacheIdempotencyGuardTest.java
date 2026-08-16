package io.ddd4j.web.core.idempotency;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CacheIdempotencyGuardTest {

    private static final String TEST_CACHE = "web-core-guard-test";

    @Mock
    private Cache<String, Object> plainCache;

    @AfterEach
    void tearDown() {
        CacheKit.unregister(TEST_CACHE);
    }

    @Test
    void defaultAndCustomConstruction() {
        assertDoesNotThrow(() -> new CacheIdempotencyGuard());
        assertDoesNotThrow(() -> new CacheIdempotencyGuard("custom-cache"));
    }

    @Test
    void rejectsBlankCacheName() {
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard(""));
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard("  "));
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard(null));
    }

    @Test
    void acquireRejectsNullKey() {
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard();
        assertThrows(IllegalArgumentException.class, () -> guard.acquire(null, Duration.ofMinutes(1)));
    }

    @Test
    void acquireRejectsUnregisteredCache() {
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard("never-registered-cache");
        assertThrows(IllegalStateException.class, () -> guard.acquire("key", Duration.ofMinutes(1)));
    }

    @Test
    void acquireRejectsCacheWithoutCasSupport() {
        CacheKit.register(TEST_CACHE, plainCache);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);
        assertThrows(IllegalStateException.class, () -> guard.acquire("key", Duration.ofMinutes(1)));
        assertThrows(IllegalStateException.class,
                () -> guard.acquireLease("key", Duration.ofMinutes(1)));
    }

    @Test
    void acquireCompleteAndReleaseWithRegisteredCasCache() {
        CacheKit.build(TEST_CACHE, 300L);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);

        assertTrue(guard.acquire("key-1", Duration.ofMinutes(5)));
        assertFalse(guard.acquire("key-1", Duration.ofMinutes(5)));

        guard.complete("key-1");
        // COMPLETED 占位仍在 TTL 内，重复 acquire 应被拒绝
        assertFalse(guard.acquire("key-1", Duration.ofMinutes(5)));

        assertTrue(guard.acquire("key-2", Duration.ofMinutes(5)));
        guard.release("key-2");
        assertTrue(guard.acquire("key-2", Duration.ofMinutes(5)));
        guard.release("key-2");
    }

    @Test
    void acquireLeaseCompleteAndReleaseWithOwnerToken() {
        CacheKit.build(TEST_CACHE, 300L);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);

        Optional<IdempotencyLease> lease = guard.acquireLease("key-3", Duration.ofMinutes(5));
        assertTrue(lease.isPresent());
        assertFalse(guard.acquireLease("key-3", Duration.ofMinutes(5)).isPresent());

        guard.complete(lease.get());
        assertFalse(guard.acquireLease("key-3", Duration.ofMinutes(5)).isPresent());

        Optional<IdempotencyLease> lease4 = guard.acquireLease("key-4", Duration.ofMinutes(5));
        assertTrue(lease4.isPresent());
        guard.release(lease4.get());
        Optional<IdempotencyLease> reacquired = guard.acquireLease("key-4", Duration.ofMinutes(5));
        assertTrue(reacquired.isPresent());
        guard.release(reacquired.get());
    }

    @Test
    void leaseWithoutOwnerTokenDelegatesToKeyMethods() {
        CacheKit.build(TEST_CACHE, 300L);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);

        assertTrue(guard.acquire("key-5", Duration.ofMinutes(5)));
        IdempotencyLease lease = new IdempotencyLease("key-5", null, Duration.ofMinutes(5));
        guard.complete(lease);
        assertFalse(guard.acquire("key-5", Duration.ofMinutes(5)));

        assertTrue(guard.acquire("key-6", Duration.ofMinutes(5)));
        guard.release(new IdempotencyLease("key-6", null, Duration.ofMinutes(5)));
        assertTrue(guard.acquire("key-6", Duration.ofMinutes(5)));
        guard.release("key-6");
    }

    @Test
    void validatesTtlAndKeyArguments() {
        CacheKit.build(TEST_CACHE, 300L);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);
        assertThrows(NullPointerException.class, () -> guard.acquire("key", null));
        assertThrows(IllegalArgumentException.class, () -> guard.acquire("key", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> guard.acquire("key", Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> guard.acquire("  ", Duration.ofMinutes(1)));
        assertThrows(NullPointerException.class, () -> guard.complete((IdempotencyLease) null));
        assertThrows(NullPointerException.class, () -> guard.release((IdempotencyLease) null));
        assertThrows(IllegalArgumentException.class, () -> guard.complete(""));
        assertThrows(IllegalArgumentException.class, () -> guard.release(""));
    }

    @Test
    void leaseTtlIsPreserved() {
        CacheKit.build(TEST_CACHE, 300L);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);

        Optional<IdempotencyLease> lease = guard.acquireLease("key-7", Duration.ofMinutes(7));
        assertTrue(lease.isPresent());
        assertEquals(Duration.ofMinutes(7), lease.get().ttl());
        guard.release(lease.get());
    }
}
