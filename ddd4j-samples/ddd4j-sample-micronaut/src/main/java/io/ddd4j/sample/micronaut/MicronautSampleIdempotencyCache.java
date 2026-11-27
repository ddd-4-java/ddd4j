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

    /** \u5546\u54c1\u4ea4\u6613\u8bf7\u6c42\u5e42\u7b49\u68c0\u67e5\u4f7f\u7528\u7684\u672c\u5730\u7f13\u5b58\u540d\u5b57\u3002 */
    private static final String CACHE_NAME = "ddd4j-web-idempotency";
    /** \u53bb\u91cd\u8bb0\u5f55\u5728\u793a\u4f8b\u7f13\u5b58\u4e2d\u4fdd\u7559\u7684\u79d2\u6570\u3002 */
    private static final long CACHE_TTL_SECONDS = 300L;

    /**
     * 初始化示例幂等缓存。
     */
    public MicronautSampleIdempotencyCache() {
        CacheKit.build(CACHE_NAME, CACHE_TTL_SECONDS);
    }

    /** \u5728 Micronaut \u505c\u6b62\u65f6\u64a4\u9500\u5f53\u524d\u793a\u4f8b\u62e5\u6709\u7684\u5e42\u7b49\u7f13\u5b58\u6ce8\u518c\u3002 */
    @PreDestroy
    void destroy() {
        CacheKit.unregister(CACHE_NAME);
    }
}
