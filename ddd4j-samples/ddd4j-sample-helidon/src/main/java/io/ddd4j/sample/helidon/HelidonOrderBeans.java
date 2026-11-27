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

    /** \u5b58\u653e\u5546\u54c1\u4ea4\u6613\u8bf7\u6c42\u5e42\u7b49\u8bb0\u5f55\u7684\u7f13\u5b58\u540d\u79f0\u3002 */
    private static final String IDEMPOTENCY_CACHE_NAME = "ddd4j-web-idempotency";
    /** \u5e42\u7b49\u7f13\u5b58\u6761\u76ee\u5b58\u6d3b\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u79d2\u3002 */
    private static final long IDEMPOTENCY_CACHE_TTL_SECONDS = 300L;

    /** \u7531\u5f53\u524d Helidon \u5b9e\u4f8b\u521b\u5efa\u5e76\u8d1f\u8d23\u6ce8\u9500\u7684\u7f13\u5b58\u3002 */
    private Cache<String, Object> ownedIdempotencyCache;

    /**
     * \u5e94\u7528\u542f\u52a8\u540e\u767b\u8bb0\u4f9b\u5546\u54c1\u4ea4\u6613\u62e6\u622a\u5668\u590d\u7528\u7684\u5e42\u7b49\u7f13\u5b58\u3002
     * @param startup \u6846\u67b6\u542f\u52a8\u9636\u6bb5\u53d1\u51fa\u7684\u5e94\u7528\u4e8b\u4ef6
     */
    void initializeIdempotencyCache(@jakarta.enterprise.event.Observes
                                    @jakarta.enterprise.context.Initialized(ApplicationScoped.class) Object startup) {
        ownedIdempotencyCache = CacheKit.buildLocalIfAbsent(IDEMPOTENCY_CACHE_NAME, IDEMPOTENCY_CACHE_TTL_SECONDS);
    }

    /** \u5e94\u7528\u505c\u6b62\u540e\u4ec5\u6e05\u7406\u5f53\u524d\u793a\u4f8b\u521b\u5efa\u7684\u5e42\u7b49\u7f13\u5b58\u3002 */
    @PreDestroy
    void destroyIdempotencyCache() {
        CacheKit.unregisterLocalIfOwned(IDEMPOTENCY_CACHE_NAME, ownedIdempotencyCache);
    }
}
