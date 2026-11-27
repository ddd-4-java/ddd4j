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

    /**
     * 常量值 {@code "ddd4j-web-idempotency"}。
     */
    public static final String DEFAULT_CACHE_NAME = "ddd4j-web-idempotency";
    /**
     * 幂等记录表示请求尚在执行中的状态。
     */
    private static final String PROCESSING = "PROCESSING";
    /**
     * 幂等记录表示请求已经成功完成的状态。
     */
    private static final String COMPLETED = "COMPLETED";
    /**
     * 用于注册或定位缓存实例的名称。
     */
    private final String cacheName;

    /**
     * 使用默认缓存业务名创建幂等守卫。
     */
    public CacheIdempotencyGuard() {
        this(DEFAULT_CACHE_NAME);
    }

    /**
     * 使用指定缓存业务名创建幂等守卫。
     *
     * @param cacheName 缓存业务名
     */
    public CacheIdempotencyGuard(String cacheName) {
        if (StrKit.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName must not be blank");
        }
        this.cacheName = cacheName;
    }

    /**
     * 要求租约时长为正，并把不足一秒的部分向上取整。
     *
     * @param ttl 请求的幂等记录保留时间
     * @return 缓存使用的超时秒数
     * @throws IllegalArgumentException 租约时长为零或负数
     */
    private static long expirySeconds(Duration ttl) {
        Duration effectiveTtl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        long seconds = effectiveTtl.getSeconds();
        if (effectiveTtl.getNano() > 0 && seconds < Long.MAX_VALUE) {
            return seconds + 1;
        }
        return seconds;
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
        long expirySeconds = expirySeconds(ttl);
        boolean acquired = CacheKit.putIfAbsent(cacheName, key, PROCESSING);
        if (acquired) {
            CacheKit.expire(cacheName, key, expirySeconds);
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
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(new IdempotencyLease(key, ownerToken, ttl))</code>
     */
    @Override
    public Optional<IdempotencyLease> acquireLease(String key, Duration ttl) {
        requireKey(key);
        requireCasCapableCache();
        long expirySeconds = expirySeconds(ttl);
        String ownerToken = PROCESSING + ':' + UUID.randomUUID();
        if (!CacheKit.putIfAbsent(cacheName, key, ownerToken)) {
            return Optional.empty();
        }
        CacheKit.expire(cacheName, key, expirySeconds);
        return Optional.of(new IdempotencyLease(key, ownerToken, ttl));
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
        long expirySeconds = expirySeconds(effectiveLease.ttl());
        if (CacheKit.replace(cacheName, effectiveLease.key(), effectiveLease.ownerToken(), COMPLETED)) {
            CacheKit.expire(cacheName, effectiveLease.key(), expirySeconds);
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

    /**
     * 取得支持原子更新的幂等缓存。
     */
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
     * 要求幂等键包含有效文本。
     *
     * @param key 当前写入的上下文键、幂等键或属性名
     * @throws IllegalArgumentException 幂等键为空白
     */
    private void requireKey(String key) {
        if (StrKit.isBlank(key)) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }
}
