package io.ddd4j.web.core;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.kit.lang.StrKit;

import java.time.Duration;
import java.util.Objects;

/**
 * 基于 CacheKit CAS 能力的默认幂等守卫。
 */
public final class CacheIdempotencyGuard implements IdempotencyGuard {

    public static final String DEFAULT_CACHE_NAME = "ddd4j-web-idempotency";
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";
    private final String cacheName;

    public CacheIdempotencyGuard() {
        this(DEFAULT_CACHE_NAME);
    }

    public CacheIdempotencyGuard(String cacheName) {
        if (StrKit.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName must not be blank");
        }
        this.cacheName = cacheName;
        initializeLocalFallback();
    }

    @Override
    public boolean acquire(String key, Duration ttl) {
        requireKey(key);
        Duration effectiveTtl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        boolean acquired = CacheKit.putIfAbsent(cacheName, key, PROCESSING);
        if (acquired) {
            CacheKit.expire(cacheName, key, effectiveTtl.toSeconds());
        }
        return acquired;
    }

    @Override
    public void complete(String key) {
        requireKey(key);
        CacheKit.replace(cacheName, key, PROCESSING, COMPLETED);
    }

    @Override
    public void release(String key) {
        requireKey(key);
        CacheKit.removeIf(cacheName, key, PROCESSING);
    }

    private void initializeLocalFallback() {
        synchronized (CacheIdempotencyGuard.class) {
            if (Objects.isNull(CacheKit.getCache(cacheName))) {
                CacheKit.build(cacheName, 300L);
            }
        }
    }

    private void requireKey(String key) {
        if (StrKit.isBlank(key)) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }
}
