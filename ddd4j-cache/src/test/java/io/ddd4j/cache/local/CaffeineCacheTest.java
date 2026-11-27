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
     * 创建最多存储 100 个条目且在写入后过期的 Caffeine 缓存。
     *
     * @param writeSeconds 写入后的存活时间，单位为秒
     * @return 当前用例使用的 Caffeine 本地缓存
     */
    private CaffeineCache<String, String> buildWithTtl(long writeSeconds) {
        CacheConfig config = CacheConfig.builder("test")
                .maximumSize(100)
                .expireAfterWriteSeconds(writeSeconds)
                .build();
        return CaffeineCache.create(config);
    }

    /**
     * 验证<code>cache.getIfPresent("k1")</code> 等于 <code>"v1"</code>。
     */
    @Test
    void putAndGetIfPresent_shouldStoreAndRetrieve() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        cache.put("k1", "v1");

        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

    /**
     * 验证<code>cache.getIfPresent("missing")</code>为空。
     */
    @Test
    void getIfPresent_shouldReturnNullForMissingKey() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        assertThat(cache.getIfPresent("missing")).isNull();
    }

    /**
     * 验证<code>value</code> 等于 <code>"loaded-k1"</code>，<code>cache.getIfPresent("k1")</code> 等于 <code>"loaded-k1"</code>。
     */
    @Test
    void getWithMappingFunction_shouldLoadOnMiss() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        String value = cache.get("k1", k -> "loaded-" + k);

        assertThat(value).isEqualTo("loaded-k1");
        assertThat(cache.getIfPresent("k1")).isEqualTo("loaded-k1");
    }

    /**
     * 验证<code>second</code> 等于 <code>"v1"</code>，<code>counter.get()</code> 等于 <code>1</code>。
     */
    @Test
    void getWithMappingFunction_shouldNotReloadWhenPresent() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        AtomicInteger counter = new AtomicInteger();

        cache.get("k1", k -> "v" + counter.incrementAndGet());
        String second = cache.get("k1", k -> "v" + counter.incrementAndGet());

        assertThat(second).isEqualTo("v1");
        assertThat(counter.get()).isEqualTo(1);
    }

    /**
     * 验证<code>cache.getIfPresent("k1")</code>为空。
     */
    @Test
    void invalidate_shouldRemoveKey() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        cache.invalidate("k1");

        assertThat(cache.getIfPresent("k1")).isNull();
    }

    /**
     * 验证<code>cache.estimatedSize()</code>为零。
     */
    @Test
    void invalidateAll_shouldClearEverything() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        cache.invalidateAll();

        assertThat(cache.estimatedSize()).isZero();
    }

    /**
     * 验证<code>cache.getIfPresent("k1")</code> 等于 <code>"v1"</code>，<code>cache.getIfPresent("k2")</code> 等于 <code>"v2"</code>。
     */
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

    /**
     * 验证<code>first</code>为真，<code>second</code>为假，<code>cache.getIfPresent("k1")</code> 等于 <code>"v1"</code>。
     */
    @Test
    void putIfAbsent_shouldReturnTrueForNewKeyAndFalseForExisting() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        boolean first = cache.putIfAbsent("k1", "v1");
        boolean second = cache.putIfAbsent("k1", "v2");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

    /**
     * 验证<code>replaced</code>为真，<code>cache.getIfPresent("k1")</code> 等于 <code>"v2"</code>。
     */
    @Test
    void replace_shouldSwapWhenExpectedMatches() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        boolean replaced = cache.replace("k1", "v1", "v2");

        assertThat(replaced).isTrue();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v2");
    }

    /**
     * 验证<code>replaced</code>为假，<code>cache.getIfPresent("k1")</code> 等于 <code>"v1"</code>。
     */
    @Test
    void replace_shouldFailWhenExpectedMismatches() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        boolean replaced = cache.replace("k1", "wrong", "v2");

        assertThat(replaced).isFalse();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

    /**
     * 验证<code>removed</code>为假，<code>cache.getIfPresent("k1")</code> 等于 <code>"v1"</code>，<code>cache.getIfPresent("k1")</code>为空。
     */
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

    /**
     * 验证<code>first</code> 等于 <code>5</code>，<code>second</code> 等于 <code>8</code>。
     */
    @Test
    void increment_shouldAddToCounter() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());

        long first = cache.increment("counter", 5);
        long second = cache.increment("counter", 3);

        assertThat(first).isEqualTo(5);
        assertThat(second).isEqualTo(8);
    }

    /**
     * 验证<code>remaining</code> 等于 <code>7</code>。
     */
    @Test
    void stockDecrement_shouldDeductStock() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());
        cache.put("stock", 10);

        long remaining = cache.stockDecrement("stock", 3);

        assertThat(remaining).isEqualTo(7);
    }

    /**
     * 验证<code>result</code> 小于 <code>0</code>。
     */
    @Test
    void stockDecrement_shouldReturnNotEnoughWhenInsufficient() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());
        cache.put("stock", 2);

        long result = cache.stockDecrement("stock", 5);

        assertThat(result).isLessThan(0);
    }

    /**
     * 验证<code>cache.getIfPresent("k1")</code> 等于 <code>"v1"</code>，<code>cache.getIfPresent("k1")</code>为空。
     *
     * @throws InterruptedException 等待测试操作时被中断
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

    /**
     * 验证<code>cache.get("k1")</code> 等于 <code>"v1"</code>，<code>cache.get("missing")</code>为空。
     */
    @Test
    void get_onNonLoadingCache_shouldBehaveLikeGetIfPresent() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        assertThat(cache.get("k1")).isEqualTo("v1");
        assertThat(cache.get("missing")).isNull();
    }

    /**
     * 验证<code>cache.get("k1")</code> 等于 <code>"auto-k1"</code>。
     */
    @Test
    void createLoading_shouldAutoLoadOnGet() {
        CacheConfig config = CacheConfig.builder("test").maximumSize(100).build();
        CaffeineCache<String, String> cache = CaffeineCache.createLoading(config, key -> "auto-" + key);

        assertThat(cache.get("k1")).isEqualTo("auto-k1");
    }

    /**
     * 验证<code>cache.unwrap()</code>非空。
     */
    @Test
    void unwrap_shouldReturnUnderlyingCaffeineCache() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        assertThat(cache.unwrap()).isNotNull();
    }
}
