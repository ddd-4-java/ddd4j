/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.cache.local;

import io.ddd4j.core.cache.CacheConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证三线本地缓存的回调式 CAS 可观察行为。
 */
class LocalCacheCasParityTest {

    /** \u5bf9\u6bd4 Caffeine \u548c Guava \u539f\u5b50\u66f4\u65b0\u65f6\u5171\u7528\u7684\u7f13\u5b58\u914d\u7f6e\u3002 */
    private static final CacheConfig CONFIG = CacheConfig.builder("local-cas-parity")
            .maximumSize(100)
            .build();

    /** \u9a8c\u8bc1<code>current.key()</code> \u7b49\u4e8e <code>"key"</code>\uff0c<code>current.value()</code>\u4e3a\u7a7a\uff0c<code>cache.getIfPresent("key")</code> \u7b49\u4e8e <code>"updated"</code>\u3002 */
    @Test
    void caffeineShouldExposeCurrentValueAndStoreComputedValue() {
        CaffeineCache<String, String> cache = CaffeineCache.create(CONFIG);

        assertEquals("initial", cache.compareAndSet("key", 60, current -> {
            assertEquals("key", current.key());
            assertNull(current.value());
            return "initial";
        }));
        assertEquals("updated", cache.compareAndSet("key", 60, current -> {
            assertEquals("initial", current.value());
            return "updated";
        }));
        assertEquals("updated", cache.getIfPresent("key"));
    }

    /** \u9a8c\u8bc1<code>current.key()</code> \u7b49\u4e8e <code>"key"</code>\uff0c<code>current.value()</code>\u4e3a\u7a7a\uff0c<code>cache.getIfPresent("key")</code> \u7b49\u4e8e <code>"updated"</code>\u3002 */
    @Test
    void guavaShouldExposeCurrentValueAndStoreComputedValue() {
        GuavaCache<String, String> cache = GuavaCache.create(CONFIG);

        assertEquals("initial", cache.compareAndSet("key", 60, current -> {
            assertEquals("key", current.key());
            assertNull(current.value());
            return "initial";
        }));
        assertEquals("updated", cache.compareAndSet("key", 60, current -> {
            assertEquals("initial", current.value());
            return "updated";
        }));
        assertEquals("updated", cache.getIfPresent("key"));
    }
}
