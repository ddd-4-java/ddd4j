package io.ddd4j.kit.cache;

import com.google.common.cache.*;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 谷歌本地缓存工具
 */
@UtilityClass
public class GCacheKit {
    private final Map<String, Cache<String, Object>> LOCAL_CACHES = new ConcurrentHashMap<>();

    public void buildAfterWrite(String biz, int expiredSeconds) {
        Cache<String, Object> cache = CacheBuilder.newBuilder().expireAfterWrite(expiredSeconds, TimeUnit.SECONDS).build();
        LOCAL_CACHES.put(biz, cache);
    }

    public void buildAfterWrite(String biz, int expiredSeconds, Consumer<String> expiredCallback) {
        Cache<String, Object> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(expiredSeconds, TimeUnit.SECONDS)
                .removalListener((RemovalListener<String, Object>) notification -> {
                    if (notification.getCause() == RemovalCause.EXPIRED) {
                        expiredCallback.accept(notification.getKey());
                    }
                })
                .build();
        LOCAL_CACHES.put(biz, cache);
    }

    public void buildAfterAccess(String biz, int expiredSeconds) {
        Cache<String, Object> cache = CacheBuilder.newBuilder().expireAfterAccess(expiredSeconds, TimeUnit.SECONDS).build();
        LOCAL_CACHES.put(biz, cache);
    }

    public void buildAfterAccess(String biz, int expiredSeconds, Consumer<String> expiredCallback) {
        Cache<String, Object> cache = CacheBuilder.newBuilder()
                .expireAfterAccess(expiredSeconds, TimeUnit.SECONDS)
                .removalListener((RemovalListener<String, Object>) notification -> {
                    if (notification.getCause() == RemovalCause.EXPIRED) {
                        expiredCallback.accept(notification.getKey());
                    }
                })
                .build();
        LOCAL_CACHES.put(biz, cache);
    }

    public synchronized <T> T get(String biz, String key, Supplier<T> defaultValue) {
        Cache<String, Object> cache = LOCAL_CACHES.get(biz);
        Object value = cache.getIfPresent(key);
        if (value == null) {
            if (defaultValue != null) {
                value = defaultValue.get();
            }
            if (value == null) return null;
            cache.put(key, value);
        }
        return (T) value;
    }

    public <T> T get(String biz, String key) {
        return get(biz, key, null);
    }

    public <T> void put(String biz, String key, T value) {
        Cache<String, Object> cache = LOCAL_CACHES.get(biz);
        cache.put(key, value);
    }

    public boolean exist(String biz, String key) {
        Cache<String, Object> cache = LOCAL_CACHES.get(biz);
        if (cache == null) return false;
        Object value = cache.getIfPresent(key);
        return value != null;
    }

    public void evict(String biz, String key) {
        Cache<String, Object> cache = LOCAL_CACHES.get(biz);
        if (cache.asMap().containsKey(key)) {
            cache.invalidate(key);
        }
    }

}
