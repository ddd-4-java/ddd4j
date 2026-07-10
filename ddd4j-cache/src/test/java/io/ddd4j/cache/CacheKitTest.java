package io.ddd4j.cache;

import io.ddd4j.cache.CacheKit.LocalCacheType;
import io.ddd4j.core.cache.CacheStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CacheKit} facade.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CacheKitTest {

    private static final String BIZ = "cache-kit-test";

    @BeforeEach
    void setUp() {
        CacheKit.unregister(BIZ);
        CacheKit.setDefaultType(LocalCacheType.CAFFEINE);
    }

    @AfterEach
    void tearDown() {
        CacheKit.unregister(BIZ);
    }

    @Test
    void build_shouldCreateLocalCache() {
        CacheKit.build(BIZ, 300);

        assertThat(CacheKit.getCacheNames()).contains(BIZ);
    }

    @Test
    void build_withBuilder_shouldCreateLocalCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10).expireAfterWriteSeconds(60));

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
    }

    @Test
    void build_withGuavaType_shouldCreateGuavaCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.GUAVA);

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
        assertThat(CacheKit.getCacheNames()).contains(BIZ);
    }

    @Test
    void build_withHutoolType_shouldCreateHutoolCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.HUTOOL);

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
    }

    @Test
    void putAndGet_shouldStoreAndRetrieveValue() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");

        assertThat((String) CacheKit.get(BIZ, "k1")).isEqualTo("v1");
    }

    @Test
    void get_shouldReturnNullForMissingKey() {
        CacheKit.build(BIZ, 300);

        assertThat((Object) CacheKit.get(BIZ, "missing")).isNull();
    }

    @Test
    void get_shouldReturnNullWhenBizNotRegistered() {
        assertThat((Object) CacheKit.get("non-existent-biz", "k")).isNull();
    }

    @Test
    void getWithLoader_shouldLoadOnMiss() {
        CacheKit.build(BIZ, 300);
        AtomicInteger loadCount = new AtomicInteger();

        String value = CacheKit.get(BIZ, "k1", k -> {
            loadCount.incrementAndGet();
            return "loaded";
        });

        assertThat(value).isEqualTo("loaded");
        assertThat(loadCount.get()).isEqualTo(1);

        // second call should hit cache, not reload
        CacheKit.get(BIZ, "k1", k -> {
            loadCount.incrementAndGet();
            return "loaded-again";
        });
        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void invalidate_shouldRemoveSingleKey() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.put(BIZ, "k2", "v2");

        CacheKit.invalidate(BIZ, "k1");

        assertThat((Object) CacheKit.get(BIZ, "k1")).isNull();
        assertThat((String) CacheKit.get(BIZ, "k2")).isEqualTo("v2");
    }

    @Test
    void invalidateAll_shouldClearAllKeys() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.put(BIZ, "k2", "v2");

        CacheKit.invalidateAll(BIZ);

        assertThat((Object) CacheKit.get(BIZ, "k1")).isNull();
        assertThat((Object) CacheKit.get(BIZ, "k2")).isNull();
    }

    @Test
    void exists_shouldReportPresence() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");

        assertThat(CacheKit.exists(BIZ, "k1")).isTrue();
        assertThat(CacheKit.exists(BIZ, "missing")).isFalse();
        assertThat(CacheKit.exists("non-existent-biz", "k1")).isFalse();
    }

    @Test
    void getStats_shouldReturnNonNullWhenRecordStatsEnabled() {
        CacheKit.build(BIZ, b -> b.maximumSize(10).recordStats(true));
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.get(BIZ, "k1");

        CacheStats stats = CacheKit.getStats(BIZ);

        assertThat(stats).isNotNull();
        assertThat(stats.hitCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void register_shouldRegisterExternalCache() {
        Map<String, Object> store = new HashMap<>();
        InMemoryCache cache = new InMemoryCache(store);

        CacheKit.register(BIZ, cache);

        assertThat(CacheKit.getCacheNames()).contains(BIZ);
        assertThat(CacheKit.getCache(BIZ)).isSameAs(cache);
    }

    @Test
    void register_shouldRejectNullBiz() {
        assertThatThrownBy(() -> CacheKit.register(null, new InMemoryCache(new HashMap<>())))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void register_shouldRejectNullCache() {
        assertThatThrownBy(() -> CacheKit.register(BIZ, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void unregister_shouldRemoveCache() {
        CacheKit.build(BIZ, 300);
        assertThat(CacheKit.getCacheNames()).contains(BIZ);

        CacheKit.unregister(BIZ);

        assertThat(CacheKit.getCacheNames()).doesNotContain(BIZ);
    }

    @Test
    void buildWithLoader_shouldAutoLoadOnGet() {
        CacheKit.buildWithLoader(BIZ, key -> "auto-" + key,
                b -> b.maximumSize(10).expireAfterWriteSeconds(60));

        String value = CacheKit.get(BIZ, "k1");

        assertThat(value).isEqualTo("auto-k1");
    }

    @Test
    void putIfAbsent_shouldRejectWhenCacheNotCasCapable() {
        // Hutool cache does not implement CasCache
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.HUTOOL);

        assertThatThrownBy(() -> CacheKit.putIfAbsent(BIZ, "k1", "v1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setDefaultType_shouldUpdateDefaultType() {
        CacheKit.setDefaultType(LocalCacheType.GUAVA);

        assertThat(CacheKit.getDefaultType()).isEqualTo(LocalCacheType.GUAVA);
    }

    // ========================= Fixtures =========================

    /**
     * Minimal in-memory Cache used to test the {@code register()} path.
     */
    static class InMemoryCache implements io.ddd4j.core.cache.Cache<String, Object> {
        private final Map<String, Object> store;

        InMemoryCache(Map<String, Object> store) {
            this.store = store;
        }

        @Override
        public Object getIfPresent(String key) {
            return store.get(key);
        }

        @Override
        public Object get(String key, java.util.function.Function<String, Object> mappingFunction) {
            return store.computeIfAbsent(key, mappingFunction);
        }

        @Override
        public void put(String key, Object value) {
            store.put(key, value);
        }

        @Override
        public void invalidate(String key) {
            store.remove(key);
        }

        @Override
        public void invalidateAll() {
            store.clear();
        }

        @Override
        public long estimatedSize() {
            return store.size();
        }

        @Override
        public CacheStats stats() {
            return null;
        }
    }
}
