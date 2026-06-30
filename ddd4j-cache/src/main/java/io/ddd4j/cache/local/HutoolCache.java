package io.ddd4j.cache.local;

import cn.hutool.cache.CacheUtil;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheStats;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Hutool 本地缓存实现。
 *
 * <p>基于 Hutool {@link cn.hutool.cache.impl.TimedCache}，仅支持写后过期（定时清理）。
 * 轻量级实现，不支持统计信息（{@link #stats()} 返回 null）。
 *
 * <p>从 ddd4j-kit/cache/impl/HutoolCache 迁移，改包名并适配新的 {@link CacheConfig}。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class HutoolCache<K, V> implements Cache<K, V> {

    private final cn.hutool.cache.impl.TimedCache<K, V> cache;

    private HutoolCache(cn.hutool.cache.impl.TimedCache<K, V> cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    /**
     * 根据 {@link CacheConfig} 创建 Hutool 缓存实例。
     *
     * <p>仅使用 {@link CacheConfig#getExpireAfterWriteSeconds()} 作为过期时间，
     * 其他配置项（最大容量、刷新策略等）不适用。
     *
     * @param config 缓存配置
     * @param <K>    键类型
     * @param <V>    值类型
     * @return HutoolCache 实例
     */
    public static <K, V> HutoolCache<K, V> create(CacheConfig config) {
        long expiredMs = (config.getExpireAfterWriteSeconds() > 0 ? config.getExpireAfterWriteSeconds() : 3600) * 1000;
        cn.hutool.cache.impl.TimedCache<K, V> timed = CacheUtil.newTimedCache(expiredMs);
        timed.schedulePrune(5000);
        return new HutoolCache<>(timed);
    }

    @Override
    public V getIfPresent(K key) {
        return cache.get(key);
    }

    @Override
    public V get(K key, Function<K, V> mappingFunction) {
        V value = cache.get(key);
        if (java.util.Objects.isNull(value)) {
            value = mappingFunction.apply(key);
            if (java.util.Objects.nonNull(value)) {
                cache.put(key, value);
            }
        }
        return value;
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void putAll(Map<K, V> map) {
        map.forEach(this::put);
    }

    @Override
    public void invalidate(K key) {
        cache.remove(key);
    }

    @Override
    public void invalidateAll() {
        cache.clear();
    }

    @Override
    public void invalidateAll(Iterable<K> keys) {
        keys.forEach(this::invalidate);
    }

    @Override
    public long estimatedSize() {
        return cache.size();
    }

    @Override
    public CacheStats stats() {
        return null;
    }

    /**
     * 获取底层 Hutool 缓存实例。
     *
     * @return Hutool TimedCache 实例
     */
    public cn.hutool.cache.impl.TimedCache<K, V> unwrap() {
        return cache;
    }

}
