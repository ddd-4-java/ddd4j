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

/**
 * 验证 <code>CacheIdempotencyGuard</code> 在<code>acquire</code>、<code>acquireCompleteAndRelease</code>、<code>acquireLeaseCompleteAndRelease</code>、<code>lease</code>等操作中的行为契约。
 */
@ExtendWith(MockitoExtension.class)
class CacheIdempotencyGuardTest {

    /**
     * 当前用例创建或查询的缓存名称。
     */
    private static final String TEST_CACHE = "web-core-guard-test";

    /**
     * 用于隔离 <code>Cache&lt;String, Object&gt;</code> 调用并记录交互的测试替身。
     */
    @Mock
    private Cache<String, Object> plainCache;

    /**
     * 用于隔离 <code>CasCache&lt;String, Object&gt;</code> 调用并记录交互的测试替身。
     */
    @Mock
    private CasCache<String, Object> casCache;

    /**
     * 测试结束后执行 <code>CacheKit.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        CacheKit.unregister(TEST_CACHE);
    }

    /**
     * 验证默认和自定义缓存配置都能够构建幂等保护组件。
     */
    @Test
    void defaultAndCustomConstruction() {
        assertDoesNotThrow(() -> new CacheIdempotencyGuard());
        assertDoesNotThrow(() -> new CacheIdempotencyGuard("custom-cache"));
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void rejectsBlankCacheName() {
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard(""));
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard("  "));
        assertThrows(IllegalArgumentException.class, () -> new CacheIdempotencyGuard(null));
    }

    /**
     * 验证<code>guard.acquire(null, Duration.ofMinutes(1))</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void acquireRejectsNullKey() {
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard();
        assertThrows(IllegalArgumentException.class, () -> guard.acquire(null, Duration.ofMinutes(1)));
    }

    /**
     * 验证<code>guard.acquire("key", Duration.ofMinutes(1))</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void acquireRejectsUnregisteredCache() {
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard("never-registered-cache");
        assertThrows(IllegalStateException.class, () -> guard.acquire("key", Duration.ofMinutes(1)));
    }

    /**
     * 验证<code>guard.acquire("key", Duration.ofMinutes(1))</code> 抛出 <code>IllegalStateException.class</code>，<code>guard.acquireLease("key", Duration.ofMinutes(1))</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void acquireRejectsCacheWithoutCasSupport() {
        CacheKit.register(TEST_CACHE, plainCache);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);
        assertThrows(IllegalStateException.class, () -> guard.acquire("key", Duration.ofMinutes(1)));
        assertThrows(IllegalStateException.class,
                () -> guard.acquireLease("key", Duration.ofMinutes(1)));
    }

    /**
     * 验证<code>guard.acquire("key-1", Duration.ofMinutes(5))</code>为真，<code>guard.acquire("key-1", Duration.ofMinutes(5))</code>为假，<code>guard.acquire("key-2", Duration.ofMinutes(5))</code>为真。
     */
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

    /**
     * 验证<code>lease.isPresent()</code>为真，<code>guard.acquireLease("key-3", Duration.ofMinutes(5)).isPresent()</code>为假，<code>reacquired.isPresent()</code>为真。
     */
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

    /**
     * 验证<code>guard.acquire("key-5", Duration.ofMinutes(5))</code>为真，<code>guard.acquire("key-5", Duration.ofMinutes(5))</code>为假，<code>guard.acquire("key-6", Duration.ofMinutes(5))</code>为真。
     */
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

    /**
     * 验证<code>guard.acquire("key", null)</code> 抛出 <code>NullPointerException.class</code>，<code>guard.acquire("key", Duration.ZERO)</code> 抛出 <code>IllegalArgumentException.class</code>，<code>guard.release("")</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
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

    /**
     * 验证<code>lease.isPresent()</code>为真，<code>lease.get().ttl()</code> 等于 <code>Duration.ofMinutes(7)</code>。
     */
    @Test
    void leaseTtlIsPreserved() {
        CacheKit.build(TEST_CACHE, 300L);
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard(TEST_CACHE);

        Optional<IdempotencyLease> lease = guard.acquireLease("key-7", Duration.ofMinutes(7));
        assertTrue(lease.isPresent());
        assertEquals(Duration.ofMinutes(7), lease.get().ttl());
        guard.release(lease.get());
    }

    /**
     * 验证<code>guard.acquire("short", Duration.ofMillis(500))</code>为真。
     */
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
