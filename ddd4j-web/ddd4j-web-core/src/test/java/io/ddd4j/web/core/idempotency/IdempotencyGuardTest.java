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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 *
 * @author ddd4j
 * @since 1.0.x
 */
class IdempotencyGuardTest {

    /** \u6d4b\u8bd5\u7f13\u5b58\u6216\u79df\u7ea6\u4f7f\u7528\u7684\u4fdd\u7559\u65f6\u957f\u3002 */
    private static final Duration TTL = Duration.ofMinutes(5);

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
        IdempotencyLease lease = guard.acquireLease("order-1", TTL).get();

        guard.complete(lease);

        assertEquals("order-1", guard.completedKey);
    }

    /** \u9a8c\u8bc1<code>guard.releasedKey</code> \u7b49\u4e8e <code>"order-1"</code>\u3002 */
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

        /** \u8bb0\u5f55\u53d7\u6d4b\u64cd\u4f5c\u662f\u5426\u6210\u529f\u83b7\u53d6\u4e86\u5f53\u524d\u8d44\u6e90\u3002 */
        private final boolean acquired;
        /** \u6700\u8fd1\u4e00\u4e2a\u5b8c\u6210\u5904\u7406\u7684\u4e1a\u52a1\u6216\u5e42\u7b49\u8bb0\u5f55\u952e\u3002 */
        private String completedKey;
        /** \u6d4b\u8bd5\u89c2\u5bdf\u5230\u9000\u51fa\u65f6\u5df2\u91ca\u653e\u7684\u5e42\u7b49\u952e\u3002 */
        private String releasedKey;

        /**
         * \u8bbe\u7f6e <code>acquired</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>RecordingGuard</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param acquired \u7528\u4e8e\u521d\u59cb\u5316 <code>RecordingGuard</code> \u7684 <code>boolean</code> \u503c
         */
        private RecordingGuard(boolean acquired) {
            this.acquired = acquired;
        }

        /**
         * acquire() 方法。
         *
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
         *
         * @param key 幂等键。
         */
        @Override
        public void complete(String key) {
            this.completedKey = key;
        }

        /**
         * release() 方法。
         *
         * @param key 幂等键。
         */
        @Override
        public void release(String key) {
            this.releasedKey = key;
        }
    }
}
