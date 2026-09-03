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

import java.time.Duration;
import java.util.Optional;

/**
 * HTTP 幂等状态机。acquire 成功后必须调用 complete 或 release。
 */
public interface IdempotencyGuard {

    boolean acquire(String key, Duration ttl);

    /**
     * 获取带所有者标识的幂等租约。
     *
     * <p>默认实现保留旧 Guard 的兼容语义；生产 Guard 应覆盖此方法并提供唯一 owner token。
     */
    default Optional<IdempotencyLease> acquireLease(String key, Duration ttl) {
        return acquire(key, ttl) ? Optional.of(new IdempotencyLease(key, null, ttl)) : Optional.empty();
    }

    void complete(String key);

    /**
     * 完成指定租约。默认实现兼容旧的按 key 完成语义。
     */
    default void complete(IdempotencyLease lease) {
        complete(lease.key());
    }

    void release(String key);

    /**
     * 释放指定租约。默认实现兼容旧的按 key 释放语义。
     */
    default void release(IdempotencyLease lease) {
        release(lease.key());
    }
}
