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
package io.ddd4j.sample.order.application;

import java.time.Duration;
import java.util.Objects;

/**
 * 在主幂等存储不可用时委派给本地降级存储的装饰器。
 *
 * <p>主存储正常返回时保留其分布式语义；只有基础设施调用失败才切换到 fallback，使核心订单交易在 Redis
 * 等外部缓存短暂不可用时仍可继续执行。
 */
public final class ResilientIdempotencyPort implements IdempotencyPort {

    private final IdempotencyPort primary;
    private final IdempotencyPort fallback;

    public ResilientIdempotencyPort(IdempotencyPort primary, IdempotencyPort fallback) {
        this.primary = Objects.requireNonNull(primary, "primary must not be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    @Override
    public boolean acquire(String key, Duration ttl) {
        try {
            return primary.acquire(key, ttl);
        } catch (RuntimeException exception) {
            return fallback.acquire(key, ttl);
        }
    }

    @Override
    public void complete(String key, Object result) {
        try {
            primary.complete(key, result);
        } catch (RuntimeException exception) {
            fallback.complete(key, result);
        }
    }

    @Override
    public void release(String key) {
        try {
            primary.release(key);
        } catch (RuntimeException exception) {
            fallback.release(key);
        }
    }
}
