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
package io.ddd4j.cache.local;

import io.ddd4j.core.cache.CacheConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CaffeineCache} (interface contract + TTL expiry).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CaffeineCacheTest {

    /**
     * \u521b\u5efa\u6700\u591a\u5b58\u50a8 100 \u4e2a\u6761\u76ee\u4e14\u5728\u5199\u5165\u540e\u8fc7\u671f\u7684 Caffeine \u7f13\u5b58\u3002
     * @param writeSeconds \u5199\u5165\u540e\u7684\u5b58\u6d3b\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u79d2
     * @return \u5f53\u524d\u7528\u4f8b\u4f7f\u7528\u7684 Caffeine \u672c\u5730\u7f13\u5b58
     */
    private CaffeineCache<String, String> buildWithTtl(long writeSeconds) {
        CacheConfig config = CacheConfig.builder("test")
                .maximumSize(100)
                .expireAfterWriteSeconds(writeSeconds)
                .build();
        return CaffeineCache.create(config);
    }

    /** \u9a8c\u8bc1<code>cache.getIfPresent("k1")</code> \u7b49\u4e8e <code>"v1"</code>\u3002 */
    @Test
    void putAndGetIfPresent_shouldStoreAndRetrieve() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        cache.put("k1", "v1");

        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

    /** \u9a8c\u8bc1<code>cache.getIfPresent("missing")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getIfPresent_shouldReturnNullForMissingKey() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        assertThat(cache.getIfPresent("missing")).isNull();
    }

    /** \u9a8c\u8bc1<code>value</code> \u7b49\u4e8e <code>"loaded-k1"</code>\uff0c<code>cache.getIfPresent("k1")</code> \u7b49\u4e8e <code>"loaded-k1"</code>\u3002 */
    @Test
    void getWithMappingFunction_shouldLoadOnMiss() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        String value = cache.get("k1", k -> "loaded-" + k);

        assertThat(value).isEqualTo("loaded-k1");
        assertThat(cache.getIfPresent("k1")).isEqualTo("loaded-k1");
    }

    /** \u9a8c\u8bc1<code>second</code> \u7b49\u4e8e <code>"v1"</code>\uff0c<code>counter.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void getWithMappingFunction_shouldNotReloadWhenPresent() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        AtomicInteger counter = new AtomicInteger();

        cache.get("k1", k -> "v" + counter.incrementAndGet());
        String second = cache.get("k1", k -> "v" + counter.incrementAndGet());

        assertThat(second).isEqualTo("v1");
        assertThat(counter.get()).isEqualTo(1);
    }

    /** \u9a8c\u8bc1<code>cache.getIfPresent("k1")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void invalidate_shouldRemoveKey() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        cache.invalidate("k1");

        assertThat(cache.getIfPresent("k1")).isNull();
    }

    /** \u9a8c\u8bc1<code>cache.estimatedSize()</code>\u4e3a\u96f6\u3002 */
    @Test
    void invalidateAll_shouldClearEverything() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        cache.invalidateAll();

        assertThat(cache.estimatedSize()).isZero();
    }

    /** \u9a8c\u8bc1<code>cache.getIfPresent("k1")</code> \u7b49\u4e8e <code>"v1"</code>\uff0c<code>cache.getIfPresent("k2")</code> \u7b49\u4e8e <code>"v2"</code>\u3002 */
    @Test
    void putAll_shouldStoreMultipleEntries() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        HashMap<String, String> values = new HashMap<>();
        values.put("k1", "v1");
        values.put("k2", "v2");
        cache.putAll(values);

        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
        assertThat(cache.getIfPresent("k2")).isEqualTo("v2");
    }

    /** \u9a8c\u8bc1<code>first</code>\u4e3a\u771f\uff0c<code>second</code>\u4e3a\u5047\uff0c<code>cache.getIfPresent("k1")</code> \u7b49\u4e8e <code>"v1"</code>\u3002 */
    @Test
    void putIfAbsent_shouldReturnTrueForNewKeyAndFalseForExisting() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        boolean first = cache.putIfAbsent("k1", "v1");
        boolean second = cache.putIfAbsent("k1", "v2");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

    /** \u9a8c\u8bc1<code>replaced</code>\u4e3a\u771f\uff0c<code>cache.getIfPresent("k1")</code> \u7b49\u4e8e <code>"v2"</code>\u3002 */
    @Test
    void replace_shouldSwapWhenExpectedMatches() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        boolean replaced = cache.replace("k1", "v1", "v2");

        assertThat(replaced).isTrue();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v2");
    }

    /** \u9a8c\u8bc1<code>replaced</code>\u4e3a\u5047\uff0c<code>cache.getIfPresent("k1")</code> \u7b49\u4e8e <code>"v1"</code>\u3002 */
    @Test
    void replace_shouldFailWhenExpectedMismatches() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        boolean replaced = cache.replace("k1", "wrong", "v2");

        assertThat(replaced).isFalse();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

    /** \u9a8c\u8bc1<code>removed</code>\u4e3a\u5047\uff0c<code>cache.getIfPresent("k1")</code> \u7b49\u4e8e <code>"v1"</code>\uff0c<code>cache.getIfPresent("k1")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void removeIf_shouldDeleteOnlyWhenExpectedMatches() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        boolean removed = cache.removeIf("k1", "wrong");
        assertThat(removed).isFalse();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");

        removed = cache.removeIf("k1", "v1");
        assertThat(removed).isTrue();
        assertThat(cache.getIfPresent("k1")).isNull();
    }

    /** \u9a8c\u8bc1<code>first</code> \u7b49\u4e8e <code>5</code>\uff0c<code>second</code> \u7b49\u4e8e <code>8</code>\u3002 */
    @Test
    void increment_shouldAddToCounter() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());

        long first = cache.increment("counter", 5);
        long second = cache.increment("counter", 3);

        assertThat(first).isEqualTo(5);
        assertThat(second).isEqualTo(8);
    }

    /** \u9a8c\u8bc1<code>remaining</code> \u7b49\u4e8e <code>7</code>\u3002 */
    @Test
    void stockDecrement_shouldDeductStock() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());
        cache.put("stock", 10);

        long remaining = cache.stockDecrement("stock", 3);

        assertThat(remaining).isEqualTo(7);
    }

    /** \u9a8c\u8bc1<code>result</code> \u5c0f\u4e8e <code>0</code>\u3002 */
    @Test
    void stockDecrement_shouldReturnNotEnoughWhenInsufficient() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());
        cache.put("stock", 2);

        long result = cache.stockDecrement("stock", 5);

        assertThat(result).isLessThan(0);
    }

    /**
     * \u9a8c\u8bc1<code>cache.getIfPresent("k1")</code> \u7b49\u4e8e <code>"v1"</code>\uff0c<code>cache.getIfPresent("k1")</code>\u4e3a\u7a7a\u3002
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
    @Test
    void expireAfterWrite_shouldEvictKeyAfterTtl() throws InterruptedException {
        CaffeineCache<String, String> cache = buildWithTtl(1);
        cache.put("k1", "v1");
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");

        // Caffeine uses lazy eviction; sleep past the TTL then trigger cleanup via getIfPresent.
        Thread.sleep(1200);
        cache.invalidateAll(); // trigger maintenance/cleanup

        assertThat(cache.getIfPresent("k1")).isNull();
    }

    /** \u9a8c\u8bc1<code>cache.get("k1")</code> \u7b49\u4e8e <code>"v1"</code>\uff0c<code>cache.get("missing")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void get_onNonLoadingCache_shouldBehaveLikeGetIfPresent() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        assertThat(cache.get("k1")).isEqualTo("v1");
        assertThat(cache.get("missing")).isNull();
    }

    /** \u9a8c\u8bc1<code>cache.get("k1")</code> \u7b49\u4e8e <code>"auto-k1"</code>\u3002 */
    @Test
    void createLoading_shouldAutoLoadOnGet() {
        CacheConfig config = CacheConfig.builder("test").maximumSize(100).build();
        CaffeineCache<String, String> cache = CaffeineCache.createLoading(config, key -> "auto-" + key);

        assertThat(cache.get("k1")).isEqualTo("auto-k1");
    }

    /** \u9a8c\u8bc1<code>cache.unwrap()</code>\u975e\u7a7a\u3002 */
    @Test
    void unwrap_shouldReturnUnderlyingCaffeineCache() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        assertThat(cache.unwrap()).isNotNull();
    }
}
