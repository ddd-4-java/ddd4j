package io.ddd4j.kit.cache.impl;

import com.google.common.cache.CacheBuilder;
import io.ddd4j.kit.cache.Cache;
import io.ddd4j.kit.cache.CacheKit;
import io.ddd4j.kit.cache.CacheStats;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 基于 Guava Cache 的缓存实现
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class GuavaCache<K, V> implements Cache<K, V> {

    private final com.google.common.cache.Cache<K, V> cache;

    private GuavaCache(CacheBuilder<Object, Object> cacheBuilder) {
        this.cache = cacheBuilder.build();
    }

    /**
     * 创建缓存实例
     */
    public static <K, V> Cache<K, V> createCache(CacheKit.CacheConfigBuilder config) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
        if (config.getMaximumSize() > 0) {
            builder.maximumSize(config.getMaximumSize());
        }
        if (config.getExpireAfterWriteSeconds() > 0) {
            builder.expireAfterWrite(config.getExpireAfterWriteSeconds(), TimeUnit.SECONDS);
        }
        if (config.getExpireAfterAccessSeconds() > 0) {
            builder.expireAfterAccess(config.getExpireAfterAccessSeconds(), TimeUnit.SECONDS);
        }
        return new GuavaCache<>(builder);
    }

    @Override
    public V getIfPresent(K key) {
        return cache.getIfPresent(key);
    }

    @Override
    public V get(K key, Function<K, V> mappingFunction) {
        try {
            return cache.get(key, () -> mappingFunction.apply(key));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load cache value", e);
        }
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void putAll(Map<K, V> map) {
        cache.putAll(map);
    }

    @Override
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll(Iterable<K> keys) {
        cache.invalidateAll(keys);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
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
