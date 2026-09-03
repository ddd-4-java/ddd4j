package io.ddd4j.cache.local;

import com.google.common.cache.CacheBuilder;
import io.ddd4j.core.cache.CASOperation;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheStats;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Guava 本地缓存实现。
 *
 * <p>基于 Google Guava Cache，支持：写后过期、访问后过期、最大容量。
 * 不支持统计信息映射（{@link #stats()} 返回 null）。
 *
 * <p>从 ddd4j-kit/cache/impl/GuavaCache 迁移，改包名并适配新的 {@link CacheConfig}。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class GuavaCache<K, V> implements Cache<K, V> {

    private final com.google.common.cache.Cache<K, V> cache;

    private GuavaCache(com.google.common.cache.Cache<K, V> cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    /**
     * 根据 {@link CacheConfig} 创建 Guava 缓存实例。
     *
     * @param config 缓存配置
     * @param <K>    键类型
     * @param <V>    值类型
     * @return GuavaCache 实例
     */
    public static <K, V> GuavaCache<K, V> create(CacheConfig config) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
        if (config.getMaximumSize() > 0) {
            builder.maximumSize(config.getMaximumSize());
        }
        if (config.getExpireAfterWriteSeconds() > 0) {
            builder.expireAfterWrite(Duration.ofSeconds(config.getExpireAfterWriteSeconds()));
        }
        if (config.getExpireAfterAccessSeconds() > 0) {
            builder.expireAfterAccess(Duration.ofSeconds(config.getExpireAfterAccessSeconds()));
        }
        return new GuavaCache<>(builder.build());
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
            throw new RuntimeException(e);
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
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @Override
    public void invalidateAll(Iterable<K> keys) {
        cache.invalidateAll(keys);
    }

    @Override
    public long estimatedSize() {
        return cache.size();
    }

    @Override
    public CacheStats stats() {
        // Guava 统计信息不映射到 ddd4j CacheStats
        return null;
    }

    // ==================== CAS 实现（基于 asMap().replace + 重试）====================

    @Override
    public V compareAndSet(K key, long expireSeconds, CASOperation<V, V> operation) {
        ConcurrentMap<K, V> map = cache.asMap();
        for (int attempt = 0; attempt < 16; attempt++) {
            V current = cache.getIfPresent(key);
            io.ddd4j.core.cache.GetsResponse<V> resp = new io.ddd4j.core.cache.GetsResponse<V>() {
                @Override
                public String key() {
                    return String.valueOf(key);
                }

                @Override
                public V value() {
                    return current;
                }
            };
            V newValue;
            try {
                newValue = operation.apply(resp);
                if (Objects.isNull(newValue)) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
            if (Objects.isNull(current)) {
                if (Objects.isNull(map.putIfAbsent(key, newValue))) {
                    return newValue;
                }
                continue;
            }
            if (map.replace(key, current, newValue)) {
                return newValue;
            }
        }
        return null;
    }

    @Override
    public boolean compareAndSet(K key, V expectedOldValue, V newValue) {
        ConcurrentMap<K, V> map = cache.asMap();
        if (Objects.isNull(expectedOldValue)) {
            return Objects.isNull(map.putIfAbsent(key, newValue));
        }
        return map.replace(key, expectedOldValue, newValue);
    }

    /**
     * 获取底层 Guava 缓存实例。
     *
     * @return Guava Cache 实例
     */
    public com.google.common.cache.Cache<K, V> unwrap() {
        return cache;
    }

}
