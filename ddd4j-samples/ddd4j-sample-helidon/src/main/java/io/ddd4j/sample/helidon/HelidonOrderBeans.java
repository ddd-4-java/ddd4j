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
package io.ddd4j.sample.helidon;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Helidon CDI 中共享 Order 端口的本地示例装配。
 */
@ApplicationScoped
public class HelidonOrderBeans {

    /**
     * 存放商品交易请求幂等记录的缓存名称。
     */
    private static final String IDEMPOTENCY_CACHE_NAME = "ddd4j-web-idempotency";
    /**
     * 幂等缓存条目存活时间，单位为秒。
     */
    private static final long IDEMPOTENCY_CACHE_TTL_SECONDS = 300L;

    /**
     * 由当前 Helidon 实例创建并负责注销的缓存。
     */
    private Cache<String, Object> ownedIdempotencyCache;

    /**
     * 应用启动后登记供商品交易拦截器复用的幂等缓存。
     *
     * @param startup 框架启动阶段发出的应用事件
     */
    void initializeIdempotencyCache(@jakarta.enterprise.event.Observes
                                    @jakarta.enterprise.context.Initialized(ApplicationScoped.class) Object startup) {
        ownedIdempotencyCache = CacheKit.buildLocalIfAbsent(IDEMPOTENCY_CACHE_NAME, IDEMPOTENCY_CACHE_TTL_SECONDS);
    }

    /**
     * 应用停止后仅清理当前示例创建的幂等缓存。
     */
    @PreDestroy
    void destroyIdempotencyCache() {
        CacheKit.unregisterLocalIfOwned(IDEMPOTENCY_CACHE_NAME, ownedIdempotencyCache);
    }
}
