package io.ddd4j.guice.cache;

import com.google.inject.AbstractModule;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ddd4j Cache 的 Guice 桥接模块。
 */
@Slf4j
public class Ddd4jCacheGuiceModule extends AbstractModule {

    private CacheKit.LocalCacheType defaultType = CacheKit.LocalCacheType.CAFFEINE;

    private final Map<String, Long> localCaches = new LinkedHashMap<>();

    private final Map<String, Function<CacheConfig.Builder, CacheConfig.Builder>> localCacheBuilders =
            new LinkedHashMap<>();

    private final Map<String, Cache<? super String, ?>> externalCaches = new LinkedHashMap<>();

    public Ddd4jCacheGuiceModule setDefaultType(CacheKit.LocalCacheType defaultType) {
        this.defaultType = defaultType;
        return this;
    }

    public Ddd4jCacheGuiceModule build(String biz, long expiredSeconds) {
        this.localCaches.put(biz, expiredSeconds);
        return this;
    }

    public Ddd4jCacheGuiceModule build(String biz, Function<CacheConfig.Builder, CacheConfig.Builder> builder) {
        this.localCacheBuilders.put(biz, builder);
        return this;
    }

    public Ddd4jCacheGuiceModule register(String biz, Cache<? super String, ?> cache) {
        this.externalCaches.put(biz, cache);
        return this;
    }

    @Override
    protected void configure() {
        CacheKit.setDefaultType(defaultType);

        for (Map.Entry<String, Long> entry : localCaches.entrySet()) {
            CacheKit.build(entry.getKey(), entry.getValue());
            log.debug("Built local cache: biz={}, expire={}s", entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Function<CacheConfig.Builder, CacheConfig.Builder>> entry :
                localCacheBuilders.entrySet()) {
            CacheKit.build(entry.getKey(), entry.getValue());
            log.debug("Built local cache with builder: biz={}", entry.getKey());
        }

        for (Map.Entry<String, Cache<? super String, ?>> entry : externalCaches.entrySet()) {
            CacheKit.register(entry.getKey(), entry.getValue());
            log.debug("Registered external cache: biz={}", entry.getKey());
        }

        log.info("Ddd4jCacheGuiceModule initialized: {} local caches, {} external caches",
                localCaches.size() + localCacheBuilders.size(), externalCaches.size());
    }
}
