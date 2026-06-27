package io.ddd4j.kit.cache.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.ddd4j.kit.cache.Cache;
import io.ddd4j.kit.cache.CacheKit;
import io.ddd4j.kit.cache.CacheStats;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 基于 Caffeine 的缓存实现
 *
 * <p>Caffeine 是 Guava Cache 的现代替代品，性能更优。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 2.0.x
 */
public class CaffeineCache<K, V> implements Cache<K, V> {

    private final com.github.benmanes.caffeine.cache.Cache<K, V> cache;

    /**
     * 构建 CaffeineCache
     */
    private CaffeineCache(Caffeine<Object, Object> caffeineBuilder) {
        this.cache = caffeineBuilder.build();
    }

    /**
     * 创建普通缓存
     */
    public static <K, V> Cache<K, V> createCache(CacheKit.CacheConfigBuilder config) {
        return new CaffeineCache<>(buildCaffeine(config));
    }

    /**
     * 创建自动加载缓存
     */
    public static <K, V> Cache<K, V> createLoadingCache(CacheKit.CacheConfigBuilder config, Function<K, V> loader) {
        Caffeine<Object, Object> builder = buildCaffeine(config);
        LoadingCache<K, V> loadingCache = builder.build(loader::apply);
        return new CaffeineCacheLoadingAdapter<>(loadingCache);
    }

    /**
     * 构建 Caffeine 配置
     */
    private static Caffeine<Object, Object> buildCaffeine(CacheKit.CacheConfigBuilder config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        if (config.getMaximumSize() > 0) {
            builder.maximumSize(config.getMaximumSize());
        }
        if (config.getExpireAfterWriteSeconds() > 0) {
            builder.expireAfterWrite(config.getExpireAfterWriteSeconds(), TimeUnit.SECONDS);
        }
        if (config.getExpireAfterAccessSeconds() > 0) {
            builder.expireAfterAccess(config.getExpireAfterAccessSeconds(), TimeUnit.SECONDS);
        }
        if (config.getRefreshAfterWriteSeconds() > 0) {
            builder.refreshAfterWrite(config.getRefreshAfterWriteSeconds(), TimeUnit.SECONDS);
        }
        if (config.getInitialCapacity() > 0) {
            builder.initialCapacity(config.getInitialCapacity());
        }
        if (config.isRecordStats()) {
            builder.recordStats();
        }
        Consumer<String> removalListener = config.getRemovalListener();
        if (removalListener != null) {
            builder.removalListener((key, value, cause) ->
                    removalListener.accept(key.toString()));
        }
        return builder;
    }

    @Override
    public V getIfPresent(K key) {
        return cache.getIfPresent(key);
    }

    @Override
    public V get(K key, Function<K, V> mappingFunction) {
        return cache.get(key, mappingFunction);
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
        return cache.estimatedSize();
    }

    @Override
    public CacheStats stats() {
        return convertStats(cache.stats());
    }

    /**
     * 转换 CacheStats
     */
    static CacheStats convertStats(com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats) {
        return new CacheStats() {
            @Override
            public long hitCount() {
                return caffeineStats.hitCount();
            }

            @Override
            public long missCount() {
                return caffeineStats.missCount();
            }

            @Override
            public double hitRate() {
                return caffeineStats.hitRate();
            }

            @Override
            public long loadCount() {
                return caffeineStats.loadCount();
            }

            @Override
            public long evictionCount() {
                return caffeineStats.evictionCount();
            }
        };
    }

    /**
     * 自动加载缓存适配器（包装 Caffeine LoadingCache 为 Cache 接口）
     */
    private static class CaffeineCacheLoadingAdapter<K, V> implements Cache<K, V> {
        private final LoadingCache<K, V> loadingCache;

        CaffeineCacheLoadingAdapter(LoadingCache<K, V> loadingCache) {
            this.loadingCache = loadingCache;
        }

        @Override
        public V getIfPresent(K key) {
            return loadingCache.getIfPresent(key);
        }

        @Override
        public V get(K key) {
            return loadingCache.get(key);
        }

        @Override
        public V get(K key, Function<K, V> mappingFunction) {
            return loadingCache.get(key, mappingFunction);
        }

        @Override
        public void put(K key, V value) {
            loadingCache.put(key, value);
        }

        @Override
        public void putAll(Map<K, V> map) {
            loadingCache.putAll(map);
        }

        @Override
        public void invalidate(K key) {
            loadingCache.invalidate(key);
        }

        @Override
        public void refresh(K key) {
            loadingCache.refresh(key);
        }

        @Override
        public void invalidateAll(Iterable<K> keys) {
            loadingCache.invalidateAll(keys);
        }

        @Override
        public void invalidateAll() {
            loadingCache.invalidateAll();
        }

        @Override
        public long estimatedSize() {
            return loadingCache.estimatedSize();
        }

        @Override
        public CacheStats stats() {
            return convertStats(loadingCache.stats());
        }
    }

}
