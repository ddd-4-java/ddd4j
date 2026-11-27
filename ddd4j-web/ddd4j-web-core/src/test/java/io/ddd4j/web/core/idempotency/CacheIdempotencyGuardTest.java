/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.core.idempotency;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CasCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** \u9a8c\u8bc1 <code>CacheIdempotencyGuard</code> \u5728<code>acquire</code>\u3001<code>acquireCompleteAndRelease</code>\u3001<code>acquireLeaseCompleteAndRelease</code>\u3001<code>lease</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class CacheIdempotencyGuardTest {

    /** \u5f53\u524d\u7528\u4f8b\u521b\u5efa\u6216\u67e5\u8be2\u7684\u7f13\u5b58\u540d\u79f0\u3002 */
    private static final String TEST_CACHE = "web-core-guard-test";

    /** \u7528\u4e8e\u9694\u79bb <code>Cache&lt;String, Object&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Cache<String, Object> plainCache;

    /** \u7528\u4e8e\u9694\u79bb <code>CasCache&lt;String, Object&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private CasCache<String, Object> casCache;

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        CacheKit.unregister(TEST_CACHE);
    }

    /** \u9a8c\u8bc1\u9ed8\u8ba4\u548c\u81ea\u5b9a\u4e49\u7f13\u5b58\u914d\u7f6e\u90fd\u80fd\u591f\u6784\u5efa\u5e42\u7b49\u4fdd\u62a4\u7ec4\u4ef6\u3002 */
    @Test
    void defaultAndCustomConstruction() {
        assertDoesNotThrow(() -> new CacheIdempotencyGuard());
        assertDoesNotThrow(() -> new CacheIdempotencyGuard("custom-cache"));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void rejectsBlankCacheName() {
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard(""));
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard("  "));
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard(null));
    }

    /** \u9a8c\u8bc1<code>guard.acquire(null, Duration.ofMinutes(1))</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void acquireRejectsNullKey() {
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard();
        assertThrows(IllegalArgumentException.class, () -> guard.acquire(null, Duration.ofMinutes(1)));
    }

    /** \u9a8c\u8bc1<code>guard.acquire("key", Duration.ofMinutes(1))</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void acquireRejectsUnregisteredCache() {
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard("never-registered-cache");
        assertThrows(IllegalStateException.class, () -> guard.acquire("key", Duration.ofMinutes(1)));
    }

    /** \u9a8c\u8bc1<code>guard.acquire("key", Duration.ofMinutes(1))</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>guard.acquireLease("key", Duration.ofMinutes(1))</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void acquireRejectsCacheWithoutCasSupport() {
        CacheKit.register(TEST_CACHE, plainCache);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);
        assertThrows(IllegalStateException.class, () -> guard.acquire("key", Duration.ofMinutes(1)));
        assertThrows(IllegalStateException.class,
                () -> guard.acquireLease("key", Duration.ofMinutes(1)));
    }

    /** \u9a8c\u8bc1<code>guard.acquire("key-1", Duration.ofMinutes(5))</code>\u4e3a\u771f\uff0c<code>guard.acquire("key-1", Duration.ofMinutes(5))</code>\u4e3a\u5047\uff0c<code>guard.acquire("key-2", Duration.ofMinutes(5))</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>lease.isPresent()</code>\u4e3a\u771f\uff0c<code>guard.acquireLease("key-3", Duration.ofMinutes(5)).isPresent()</code>\u4e3a\u5047\uff0c<code>reacquired.isPresent()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>guard.acquire("key-5", Duration.ofMinutes(5))</code>\u4e3a\u771f\uff0c<code>guard.acquire("key-5", Duration.ofMinutes(5))</code>\u4e3a\u5047\uff0c<code>guard.acquire("key-6", Duration.ofMinutes(5))</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>guard.acquire("key", null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>guard.acquire("key", Duration.ZERO)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>guard.release("")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>lease.isPresent()</code>\u4e3a\u771f\uff0c<code>lease.get().ttl()</code> \u7b49\u4e8e <code>Duration.ofMinutes(7)</code>\u3002 */
    @Test
    void leaseTtlIsPreserved() {
        CacheKit.build(TEST_CACHE, 300L);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);

        Optional<IdempotencyLease> lease = guard.acquireLease("key-7", Duration.ofMinutes(7));
        assertTrue(lease.isPresent());
        assertEquals(Duration.ofMinutes(7), lease.get().ttl());
        guard.release(lease.get());
    }

    /** \u9a8c\u8bc1<code>guard.acquire("short", Duration.ofMillis(500))</code>\u4e3a\u771f\u3002 */
    @Test
    void positiveFractionalSecondTtlNeverExpiresAtZeroSeconds() {
        CacheKit.register(TEST_CACHE, casCache);
        when(casCache.putIfAbsent(eq("short"), any())).thenReturn(true);
        when(casCache.putIfAbsent(eq("lease"), any())).thenReturn(true);
        when(casCache.replace(eq("lease"), any(), eq("COMPLETED"))).thenReturn(true);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);

        assertTrue(guard.acquire("short", Duration.ofMillis(500)));
        IdempotencyLease lease = guard.acquireLease("lease", Duration.ofMillis(1500)).get();
        guard.complete(lease);

        verify(casCache).expire("short", 1L);
        verify(casCache, times(2)).expire("lease", 2L);
    }
}
