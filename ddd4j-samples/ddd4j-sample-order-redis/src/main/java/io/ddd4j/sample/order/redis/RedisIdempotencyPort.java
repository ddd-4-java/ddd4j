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
package io.ddd4j.sample.order.redis;

import io.ddd4j.sample.order.application.IdempotencyPort;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.Objects;

/**
 * 基于 Redis 原子 {@code SET NX PX} 的支付幂等端口。
 */
public final class RedisIdempotencyPort implements IdempotencyPort {

    private static final String ACQUIRED = "ACQUIRED";
    private static final String COMPLETED = "COMPLETED";
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final UnifiedJedis jedis;

    public RedisIdempotencyPort(UnifiedJedis jedis) {
        this.jedis = Objects.requireNonNull(jedis, "jedis must not be null");
    }

    @Override
    public boolean acquire(String key, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be greater than zero");
        }
        String result = jedis.set(key, ACQUIRED, SetParams.setParams().nx().px(ttl.toMillis()));
        return Objects.equals("OK", result);
    }

    @Override
    public void complete(String key, Object result) {
        Objects.requireNonNull(key, "key must not be null");
        jedis.set(key, COMPLETED, SetParams.setParams().px(COMPLETED_TTL.toMillis()));
    }

    @Override
    public void release(String key) {
        Objects.requireNonNull(key, "key must not be null");
        jedis.del(key);
    }
}
