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
package io.ddd4j.sample.micronaut;

import io.ddd4j.cache.CacheKit;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

/**
 * Micronaut 本地示例的 Web 幂等缓存生命周期。
 *
 * <p>示例使用 Caffeine 验证 Web 幂等租约协议；生产环境必须显式注册共享的 Redis 或 Redisson CAS 缓存。
 */
@Context
@Singleton
public class MicronautSampleIdempotencyCache {

    private static final String CACHE_NAME = "ddd4j-web-idempotency";
    private static final long CACHE_TTL_SECONDS = 300L;

    public MicronautSampleIdempotencyCache() {
        CacheKit.build(CACHE_NAME, CACHE_TTL_SECONDS);
    }

    @PreDestroy
    void destroy() {
        CacheKit.unregister(CACHE_NAME);
    }
}
