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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author ddd4j
 * @since 1.0.x
 */
class IdempotencyGuardTest {

    private static final Duration TTL = Duration.ofMinutes(5);

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

    /**
     * 记录完成与释放调用的桩实现，便于断言租约到按键操作的委托行为。
     */
    private static final class RecordingGuard implements IdempotencyGuard {

        private final boolean acquired;
        private String completedKey;
        private String releasedKey;

        private RecordingGuard(boolean acquired) {
            this.acquired = acquired;
        }

        /**
         * acquire() 方法。
         * @param key 幂等键。
         * @param ttl 锁的有效期。
         * @return 判断结果。
         */
        @Override
        public boolean acquire(String key, Duration ttl) {
            return acquired;
        }

        /**
         * complete() 方法。
         * @param key 幂等键。
         */
        @Override
        public void complete(String key) {
            this.completedKey = key;
        }

        /**
         * release() 方法。
         * @param key 幂等键。
         */
        @Override
        public void release(String key) {
            this.releasedKey = key;
        }
    }
}
