package io.ddd4j.kit.cache.impl;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import io.ddd4j.kit.cache.Cache;
import io.ddd4j.kit.cache.CacheKit;
import io.ddd4j.kit.cache.CacheStats;

import java.util.Map;
import java.util.function.Function;

/**
 * 基于 Hutool 的缓存实现
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 2.0.x
 */
public class HutoolCache<K, V> implements Cache<K, V> {

    private final TimedCache<K, V> cache;

    /**
     * 构建 HutoolCache
     */
    public HutoolCache(long expiredSeconds) {
        this.cache = CacheUtil.newTimedCache(expiredSeconds * 1000);
        this.cache.schedulePrune(5000);
    }

    /**
     * 创建缓存实例
     */
    public static <K, V> Cache<K, V> createCache(CacheKit.CacheConfigBuilder config) {
        return new HutoolCache<>(config.getExpireAfterWriteSeconds());
    }

    @Override
    public V getIfPresent(K key) {
        return cache.get(key);
    }

    @Override
    public V get(K key, Function<K, V> mappingFunction) {
        V value = cache.get(key);
        if (value == null) {
            value = mappingFunction.apply(key);
            if (value != null) {
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
        map.forEach(cache::put);
    }

    @Override
    public void invalidate(K key) {
        cache.remove(key);
    }

    @Override
    public void invalidateAll(Iterable<K> keys) {
        keys.forEach(cache::remove);
    }

    @Override
    public void invalidateAll() {
        cache.clear();
    }

    @Override
    public long estimatedSize() {
        return cache.size();
    }

    @Override
    public CacheStats stats() {
        return null;
    }

}
