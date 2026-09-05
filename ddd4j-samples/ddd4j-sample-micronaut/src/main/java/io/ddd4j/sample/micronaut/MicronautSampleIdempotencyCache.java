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
