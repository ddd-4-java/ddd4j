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

    /**
     * 对比 Caffeine 和 Guava 原子更新时共用的缓存配置。
     */
    private static final CacheConfig CONFIG = CacheConfig.builder("local-cas-parity")
            .maximumSize(100)
            .build();

    /**
     * 验证<code>current.key()</code> 等于 <code>"key"</code>，<code>current.value()</code>为空，<code>cache.getIfPresent("key")</code> 等于 <code>"updated"</code>。
     */
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

    /**
     * 验证<code>current.key()</code> 等于 <code>"key"</code>，<code>current.value()</code>为空，<code>cache.getIfPresent("key")</code> 等于 <code>"updated"</code>。
     */
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
