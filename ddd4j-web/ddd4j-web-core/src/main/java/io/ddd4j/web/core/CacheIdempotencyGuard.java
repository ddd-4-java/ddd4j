package io.ddd4j.web.core;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CasCache;
import io.ddd4j.kit.lang.StrKit;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 CacheKit CAS 能力的幂等守卫。
 *
 * <p>生产环境必须预先注册具备 CAS 能力的共享缓存，例如 Redis 或 Redisson。
 * 此类不会创建本地回退缓存，避免多实例部署时出现静默失效的幂等保护。
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
    }

    @Override
    public boolean acquire(String key, Duration ttl) {
        requireKey(key);
        requireCasCapableCache();
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
    public Optional<IdempotencyLease> acquireLease(String key, Duration ttl) {
        requireKey(key);
        requireCasCapableCache();
        Duration effectiveTtl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String ownerToken = PROCESSING + ':' + UUID.randomUUID();
        if (!CacheKit.putIfAbsent(cacheName, key, ownerToken)) {
            return Optional.empty();
        }
        CacheKit.expire(cacheName, key, effectiveTtl.toSeconds());
        return Optional.of(new IdempotencyLease(key, ownerToken, effectiveTtl));
    }

    @Override
    public void complete(String key) {
        requireKey(key);
        CacheKit.replace(cacheName, key, PROCESSING, COMPLETED);
    }

    @Override
    public void complete(IdempotencyLease lease) {
        IdempotencyLease effectiveLease = Objects.requireNonNull(lease, "lease must not be null");
        if (Objects.isNull(effectiveLease.ownerToken())) {
            complete(effectiveLease.key());
            return;
        }
        if (CacheKit.replace(cacheName, effectiveLease.key(), effectiveLease.ownerToken(), COMPLETED)) {
            CacheKit.expire(cacheName, effectiveLease.key(), effectiveLease.ttl().toSeconds());
        }
    }

    @Override
    public void release(String key) {
        requireKey(key);
        CacheKit.removeIf(cacheName, key, PROCESSING);
    }

    @Override
    public void release(IdempotencyLease lease) {
        IdempotencyLease effectiveLease = Objects.requireNonNull(lease, "lease must not be null");
        if (Objects.isNull(effectiveLease.ownerToken())) {
            release(effectiveLease.key());
            return;
        }
        CacheKit.removeIf(cacheName, effectiveLease.key(), effectiveLease.ownerToken());
    }

    private void requireCasCapableCache() {
        Cache<String, Object> cache = CacheKit.getCache(cacheName);
        if (Objects.isNull(cache)) {
            throw new IllegalStateException("idempotency cache must be explicitly registered: " + cacheName);
        }
        if (!(cache instanceof CasCache)) {
            throw new IllegalStateException("idempotency cache must support CAS: " + cacheName);
        }
    }

    private void requireKey(String key) {
        if (StrKit.isBlank(key)) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }
}
