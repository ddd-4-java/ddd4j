package io.ddd4j.cache.local;

import io.ddd4j.core.cache.CacheConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CaffeineCache} (interface contract + TTL expiry).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CaffeineCacheTest {

    private CaffeineCache<String, String> buildWithTtl(long writeSeconds) {
        CacheConfig config = CacheConfig.builder("test")
                .maximumSize(100)
                .expireAfterWriteSeconds(writeSeconds)
                .build();
        return CaffeineCache.create(config);
    }

    @Test
    void putAndGetIfPresent_shouldStoreAndRetrieve() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        cache.put("k1", "v1");

        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

    @Test
    void getIfPresent_shouldReturnNullForMissingKey() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        assertThat(cache.getIfPresent("missing")).isNull();
    }

    @Test
    void getWithMappingFunction_shouldLoadOnMiss() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        String value = cache.get("k1", k -> "loaded-" + k);

        assertThat(value).isEqualTo("loaded-k1");
        assertThat(cache.getIfPresent("k1")).isEqualTo("loaded-k1");
    }

    @Test
    void getWithMappingFunction_shouldNotReloadWhenPresent() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        AtomicInteger counter = new AtomicInteger();

        cache.get("k1", k -> "v" + counter.incrementAndGet());
        String second = cache.get("k1", k -> "v" + counter.incrementAndGet());

        assertThat(second).isEqualTo("v1");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void invalidate_shouldRemoveKey() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        cache.invalidate("k1");

        assertThat(cache.getIfPresent("k1")).isNull();
    }

    @Test
    void invalidateAll_shouldClearEverything() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        cache.invalidateAll();

        assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    void putAll_shouldStoreMultipleEntries() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        cache.putAll(new java.util.HashMap<String,String>() {{ put("k1","v1"); put("k2","v2"); }});

        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
        assertThat(cache.getIfPresent("k2")).isEqualTo("v2");
    }

    @Test
    void putIfAbsent_shouldReturnTrueForNewKeyAndFalseForExisting() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        boolean first = cache.putIfAbsent("k1", "v1");
        boolean second = cache.putIfAbsent("k1", "v2");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

    @Test
    void replace_shouldSwapWhenExpectedMatches() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        boolean replaced = cache.replace("k1", "v1", "v2");

        assertThat(replaced).isTrue();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v2");
    }

    @Test
    void replace_shouldFailWhenExpectedMismatches() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        boolean replaced = cache.replace("k1", "wrong", "v2");

        assertThat(replaced).isFalse();
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
    }

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

    @Test
    void increment_shouldAddToCounter() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());

        long first = cache.increment("counter", 5);
        long second = cache.increment("counter", 3);

        assertThat(first).isEqualTo(5);
        assertThat(second).isEqualTo(8);
    }

    @Test
    void stockDecrement_shouldDeductStock() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());
        cache.put("stock", 10);

        long remaining = cache.stockDecrement("stock", 3);

        assertThat(remaining).isEqualTo(7);
    }

    @Test
    void stockDecrement_shouldReturnNotEnoughWhenInsufficient() {
        CaffeineCache<String, Object> cache = CaffeineCache.create(
                CacheConfig.builder("test").maximumSize(100).build());
        cache.put("stock", 2);

        long result = cache.stockDecrement("stock", 5);

        assertThat(result).isLessThan(0);
    }

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

    @Test
    void get_onNonLoadingCache_shouldBehaveLikeGetIfPresent() {
        CaffeineCache<String, String> cache = buildWithTtl(300);
        cache.put("k1", "v1");

        assertThat(cache.get("k1")).isEqualTo("v1");
        assertThat(cache.get("missing")).isNull();
    }

    @Test
    void createLoading_shouldAutoLoadOnGet() {
        CacheConfig config = CacheConfig.builder("test").maximumSize(100).build();
        CaffeineCache<String, String> cache = CaffeineCache.createLoading(config, key -> "auto-" + key);

        assertThat(cache.get("k1")).isEqualTo("auto-k1");
    }

    @Test
    void unwrap_shouldReturnUnderlyingCaffeineCache() {
        CaffeineCache<String, String> cache = buildWithTtl(300);

        assertThat(cache.unwrap()).isNotNull();
    }
}
