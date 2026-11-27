package io.ddd4j.web.core.idempotency;

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

    /** \u5e38\u91cf\u503c {@code "ddd4j-web-idempotency"}\u3002 */
    public static final String DEFAULT_CACHE_NAME = "ddd4j-web-idempotency";
    /** \u5e42\u7b49\u8bb0\u5f55\u8868\u793a\u8bf7\u6c42\u5c1a\u5728\u6267\u884c\u4e2d\u7684\u72b6\u6001\u3002 */
    private static final String PROCESSING = "PROCESSING";
    /** \u5e42\u7b49\u8bb0\u5f55\u8868\u793a\u8bf7\u6c42\u5df2\u7ecf\u6210\u529f\u5b8c\u6210\u7684\u72b6\u6001\u3002 */
    private static final String COMPLETED = "COMPLETED";
    /** \u7528\u4e8e\u6ce8\u518c\u6216\u5b9a\u4f4d\u7f13\u5b58\u5b9e\u4f8b\u7684\u540d\u79f0\u3002 */
    private final String cacheName;

    /** \u4f7f\u7528\u9ed8\u8ba4\u7f13\u5b58\u4e1a\u52a1\u540d\u521b\u5efa\u5e42\u7b49\u5b88\u536b\u3002 */
    public CacheIdempotencyGuard() {
        this(DEFAULT_CACHE_NAME);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u7f13\u5b58\u4e1a\u52a1\u540d\u521b\u5efa\u5e42\u7b49\u5b88\u536b\u3002
     * @param cacheName \u7f13\u5b58\u4e1a\u52a1\u540d
     */
    public CacheIdempotencyGuard(String cacheName) {
        if (StrKit.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName must not be blank");
        }
        this.cacheName = cacheName;
    }

    /**
     * 尝试获取幂等键。
     *
     * <p>此实现调用 <code>requireKey(key)</code>。</p>
     *
     * @param key 幂等键
     * @param ttl 有效期
     * @return 实际返回<code>acquired</code>
     */
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

    /**
     * 获取带所有者标识的幂等租约。
     *
     * <p>此实现调用 <code>requireKey(key)</code>。</p>
     *
     * @param key 幂等键
     * @param ttl 有效期
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(new IdempotencyLease(key, ownerToken, effectiveTtl))</code>
     */
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

    /**
     * 标记指定幂等键的业务处理已完成。
     *
     * <p>此实现调用 <code>requireKey(key)</code>。</p>
     *
     * @param key 幂等键
     */
    @Override
    public void complete(String key) {
        requireKey(key);
        CacheKit.replace(cacheName, key, PROCESSING, COMPLETED);
    }

    /**
     * 完成指定租约。默认实现兼容旧的按 key 完成语义。
     *
     * <p>此实现调用 <code>effectiveLease.ownerToken()</code>。</p>
     *
     * @param lease 幂等租约
     */
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

    /**
     * 释放指定幂等键，允许重试。
     *
     * <p>此实现调用 <code>requireKey(key)</code>。</p>
     *
     * @param key 幂等键
     */
    @Override
    public void release(String key) {
        requireKey(key);
        CacheKit.removeIf(cacheName, key, PROCESSING);
    }

    /**
     * 释放指定租约。默认实现兼容旧的按 key 释放语义。
     *
     * <p>此实现调用 <code>effectiveLease.ownerToken()</code>。</p>
     *
     * @param lease 幂等租约
     */
    @Override
    public void release(IdempotencyLease lease) {
        IdempotencyLease effectiveLease = Objects.requireNonNull(lease, "lease must not be null");
        if (Objects.isNull(effectiveLease.ownerToken())) {
            release(effectiveLease.key());
            return;
        }
        CacheKit.removeIf(cacheName, effectiveLease.key(), effectiveLease.ownerToken());
    }

    /** \u53d6\u5f97\u652f\u6301\u539f\u5b50\u66f4\u65b0\u7684\u5e42\u7b49\u7f13\u5b58\u3002 */
    private void requireCasCapableCache() {
        Cache<String, Object> cache = CacheKit.getCache(cacheName);
        if (Objects.isNull(cache)) {
            throw new IllegalStateException("idempotency cache must be explicitly registered: " + cacheName);
        }
        if (!(cache instanceof CasCache)) {
            throw new IllegalStateException("idempotency cache must support CAS: " + cacheName);
        }
    }

    /**
     * \u8981\u6c42\u5e42\u7b49\u952e\u5305\u542b\u6709\u6548\u6587\u672c\u3002
     * @param key \u5f53\u524d\u5199\u5165\u7684\u4e0a\u4e0b\u6587\u952e\u3001\u5e42\u7b49\u952e\u6216\u5c5e\u6027\u540d
     * @throws IllegalArgumentException \u5e42\u7b49\u952e\u4e3a\u7a7a\u767d
     */
    private void requireKey(String key) {
        if (StrKit.isBlank(key)) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }
}
