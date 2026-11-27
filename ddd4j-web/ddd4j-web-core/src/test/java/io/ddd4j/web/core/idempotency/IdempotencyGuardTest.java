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

    /**
     * 测试缓存或租约使用的保留时长。
     */
    private static final Duration TTL = Duration.ofMinutes(5);

    /**
     * 验证<code>lease.isPresent()</code>为真，<code>lease.get().key()</code> 等于 <code>"order-1"</code>，<code>lease.get().ttl()</code> 等于 <code>TTL</code>。
     */
    @Test
    void acquireLeaseReturnsLeaseWhenAcquired() {
        RecordingGuard guard = new RecordingGuard(true);

        Optional<IdempotencyLease> lease = guard.acquireLease("order-1", TTL);

        assertTrue(lease.isPresent());
        assertEquals("order-1", lease.get().key());
        assertNull(lease.get().ownerToken());
        assertEquals(TTL, lease.get().ttl());
    }

    /**
     * 验证<code>guard.acquireLease("order-1", TTL).isPresent()</code>为假。
     */
    @Test
    void acquireLeaseReturnsEmptyWhenNotAcquired() {
        RecordingGuard guard = new RecordingGuard(false);
        assertFalse(guard.acquireLease("order-1", TTL).isPresent());
    }

    /**
     * 验证<code>guard.completedKey</code> 等于 <code>"order-1"</code>。
     */
    @Test
    void completeByLeaseDelegatesToKey() {
        RecordingGuard guard = new RecordingGuard(true);
        IdempotencyLease lease = guard.acquireLease("order-1", TTL).get();

        guard.complete(lease);

        assertEquals("order-1", guard.completedKey);
    }

    /**
     * 验证<code>guard.releasedKey</code> 等于 <code>"order-1"</code>。
     */
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

        /**
         * 记录受测操作是否成功获取了当前资源。
         */
        private final boolean acquired;
        /**
         * 最近一个完成处理的业务或幂等记录键。
         */
        private String completedKey;
        /**
         * 测试观察到退出时已释放的幂等键。
         */
        private String releasedKey;

        /**
         * 设置 <code>acquired</code>，用于构造 <code>RecordingGuard</code> 测试实例。
         *
         * @param acquired 用于初始化 <code>RecordingGuard</code> 的 <code>boolean</code> 值
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
