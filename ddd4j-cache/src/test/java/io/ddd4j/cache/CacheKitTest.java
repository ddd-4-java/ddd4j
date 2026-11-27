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
package io.ddd4j.cache;

import io.ddd4j.cache.CacheKit.LocalCacheType;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CacheKit} facade.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CacheKitTest {

    /** \u6d4b\u8bd5\u671f\u95f4\u6ce8\u518c\u53ca\u6e05\u7406\u7684\u4e1a\u52a1\u7f13\u5b58\u540d\u79f0\u3002 */
    private static final String BIZ = "cache-kit-test";

    /** \u6e05\u7406\u540c\u540d\u7f13\u5b58\u5e76\u628a\u672c\u5730\u7f13\u5b58\u5b9e\u73b0\u6062\u590d\u4e3a Caffeine\u3002 */
    @BeforeEach
    void setUp() {
        CacheKit.unregister(BIZ);
        CacheKit.setDefaultType(LocalCacheType.CAFFEINE);
    }

    /** \u6ce8\u9500\u5f53\u524d\u7528\u4f8b\u4f7f\u7528\u7684\u4e1a\u52a1\u7f13\u5b58\u3002 */
    @AfterEach
    void tearDown() {
        CacheKit.unregister(BIZ);
    }

    /** \u9a8c\u8bc1<code>CacheKit.getCacheNames()</code> \u5305\u542b <code>BIZ</code>\u3002 */
    @Test
    void build_shouldCreateLocalCache() {
        CacheKit.build(BIZ, 300);

        assertThat(CacheKit.getCacheNames()).contains(BIZ);
    }

    /** \u9a8c\u8bc1<code>CacheKit.getCache(BIZ)</code>\u975e\u7a7a\u3002 */
    @Test
    void build_withBuilder_shouldCreateLocalCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10).expireAfterWriteSeconds(60));

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
    }

    /** \u9a8c\u8bc1<code>CacheKit.getCache(BIZ)</code>\u975e\u7a7a\uff0c<code>CacheKit.getCacheNames()</code> \u5305\u542b <code>BIZ</code>\u3002 */
    @Test
    void build_withGuavaType_shouldCreateGuavaCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.GUAVA);

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
        assertThat(CacheKit.getCacheNames()).contains(BIZ);
    }

    /** \u9a8c\u8bc1<code>CacheKit.getCache(BIZ)</code>\u975e\u7a7a\u3002 */
    @Test
    void build_withHutoolType_shouldCreateHutoolCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.HUTOOL);

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
    }

    /** \u9a8c\u8bc1<code>(String) CacheKit.get(BIZ, "k1")</code> \u7b49\u4e8e <code>"v1"</code>\u3002 */
    @Test
    void putAndGet_shouldStoreAndRetrieveValue() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");

        assertThat((String) CacheKit.get(BIZ, "k1")).isEqualTo("v1");
    }

    /** \u9a8c\u8bc1<code>(Object) CacheKit.get(BIZ, "missing")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void get_shouldReturnNullForMissingKey() {
        CacheKit.build(BIZ, 300);

        assertThat((Object) CacheKit.get(BIZ, "missing")).isNull();
    }

    /** \u9a8c\u8bc1<code>(Object) CacheKit.get("non-existent-biz", "k")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void get_shouldReturnNullWhenBizNotRegistered() {
        assertThat((Object) CacheKit.get("non-existent-biz", "k")).isNull();
    }

    /** \u9a8c\u8bc1<code>value</code> \u7b49\u4e8e <code>"loaded"</code>\uff0c<code>loadCount.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>(Object) CacheKit.get(BIZ, "k1")</code>\u4e3a\u7a7a\uff0c<code>(String) CacheKit.get(BIZ, "k2")</code> \u7b49\u4e8e <code>"v2"</code>\u3002 */
    @Test
    void invalidate_shouldRemoveSingleKey() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.put(BIZ, "k2", "v2");

        CacheKit.invalidate(BIZ, "k1");

        assertThat((Object) CacheKit.get(BIZ, "k1")).isNull();
        assertThat((String) CacheKit.get(BIZ, "k2")).isEqualTo("v2");
    }

    /** \u9a8c\u8bc1<code>(Object) CacheKit.get(BIZ, "k1")</code>\u4e3a\u7a7a\uff0c<code>(Object) CacheKit.get(BIZ, "k2")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void invalidateAll_shouldClearAllKeys() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.put(BIZ, "k2", "v2");

        CacheKit.invalidateAll(BIZ);

        assertThat((Object) CacheKit.get(BIZ, "k1")).isNull();
        assertThat((Object) CacheKit.get(BIZ, "k2")).isNull();
    }

    /** \u9a8c\u8bc1<code>CacheKit.exists(BIZ, "k1")</code>\u4e3a\u771f\uff0c<code>CacheKit.exists(BIZ, "missing")</code>\u4e3a\u5047\uff0c<code>CacheKit.exists("non-existent-biz", "k1")</code>\u4e3a\u5047\u3002 */
    @Test
    void exists_shouldReportPresence() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");

        assertThat(CacheKit.exists(BIZ, "k1")).isTrue();
        assertThat(CacheKit.exists(BIZ, "missing")).isFalse();
        assertThat(CacheKit.exists("non-existent-biz", "k1")).isFalse();
    }

    /** \u9a8c\u8bc1<code>stats</code>\u975e\u7a7a\uff0c<code>stats.hitCount()</code> \u5927\u4e8e\u6216\u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void getStats_shouldReturnNonNullWhenRecordStatsEnabled() {
        CacheKit.build(BIZ, b -> b.maximumSize(10).recordStats(true));
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.get(BIZ, "k1");

        CacheStats stats = CacheKit.getStats(BIZ);

        assertThat(stats).isNotNull();
        assertThat(stats.hitCount()).isGreaterThanOrEqualTo(1);
    }

    /** \u9a8c\u8bc1<code>CacheKit.getCacheNames()</code> \u5305\u542b <code>BIZ</code>\uff0c<code>CacheKit.getCache(BIZ)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>cache</code>\u3002 */
    @Test
    void register_shouldRegisterExternalCache() {
        Map<String, Object> store = new HashMap<>();
        InMemoryCache cache = new InMemoryCache(store);

        CacheKit.register(BIZ, cache);

        assertThat(CacheKit.getCacheNames()).contains(BIZ);
        assertThat(CacheKit.getCache(BIZ)).isSameAs(cache);
    }

    /** \u9a8c\u8bc1<code>CacheKit.register(null, new InMemoryCache(new HashMap&lt;&gt;()))</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void register_shouldRejectNullBiz() {
        assertThatThrownBy(() -> CacheKit.register(null, new InMemoryCache(new HashMap<>())))
                .isInstanceOf(NullPointerException.class);
    }

    /** \u9a8c\u8bc1<code>CacheKit.register(BIZ, null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void register_shouldRejectNullCache() {
        assertThatThrownBy(() -> CacheKit.register(BIZ, null))
                .isInstanceOf(NullPointerException.class);
    }

    /** \u9a8c\u8bc1<code>CacheKit.getCacheNames()</code> \u5305\u542b <code>BIZ</code>\uff0c<code>CacheKit.getCacheNames()</code> \u4e0d\u5305\u542b <code>BIZ</code>\u3002 */
    @Test
    void unregister_shouldRemoveCache() {
        CacheKit.build(BIZ, 300);
        assertThat(CacheKit.getCacheNames()).contains(BIZ);

        CacheKit.unregister(BIZ);

        assertThat(CacheKit.getCacheNames()).doesNotContain(BIZ);
    }

    /** \u9a8c\u8bc1<code>value</code> \u7b49\u4e8e <code>"auto-k1"</code>\u3002 */
    @Test
    void buildWithLoader_shouldAutoLoadOnGet() {
        CacheKit.buildWithLoader(BIZ, key -> "auto-" + key,
                b -> b.maximumSize(10).expireAfterWriteSeconds(60));

        String value = CacheKit.get(BIZ, "k1");

        assertThat(value).isEqualTo("auto-k1");
    }

    /** \u9a8c\u8bc1<code>CacheKit.putIfAbsent(BIZ, "k1", "v1")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void putIfAbsent_shouldRejectWhenCacheNotCasCapable() {
        // Hutool cache does not implement CasCache
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.HUTOOL);

        assertThatThrownBy(() -> CacheKit.putIfAbsent(BIZ, "k1", "v1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** \u9a8c\u8bc1<code>CacheKit.getDefaultType()</code> \u7b49\u4e8e <code>LocalCacheType.GUAVA</code>\u3002 */
    @Test
    void setDefaultType_shouldUpdateDefaultType() {
        CacheKit.setDefaultType(LocalCacheType.GUAVA);

        assertThat(CacheKit.getDefaultType()).isEqualTo(LocalCacheType.GUAVA);
    }

    /**
     * \u9a8c\u8bc1<code>owners</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>CacheKit.getCache(BIZ)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>owners.get(0)</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void concurrentBuildersReturnExactlyOneOwnedRegistration() throws Exception {
        int clients = 8;
        CyclicBarrier start = new CyclicBarrier(clients);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Future<Cache<String, Object>>> attempts = new ArrayList<>();
            for (int i = 0; i < clients; i++) {
                attempts.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return CacheKit.buildLocalIfAbsent(BIZ, 300);
                }));
            }
            List<Cache<String, Object>> owners = new ArrayList<>();
            for (Future<Cache<String, Object>> attempt : attempts) {
                Cache<String, Object> owned = attempt.get(10, TimeUnit.SECONDS);
                if (Objects.nonNull(owned)) {
                    owners.add(owned);
                }
            }
            assertThat(owners).hasSize(1);
            assertThat(CacheKit.getCache(BIZ)).isSameAs(owners.get(0));
        } finally {
            pool.shutdownNow();
        }
    }

    /** \u9a8c\u8bc1<code>CacheKit.buildLocalIfAbsent(BIZ, 300)</code>\u4e3a\u7a7a\uff0c<code>CacheKit.getCache(BIZ)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>replacement</code>\uff0c<code>(Object) CacheKit.get(BIZ, "key")</code> \u7b49\u4e8e <code>"external"</code>\u3002 */
    @Test
    void releasingOldOwnerPreservesReplacementAndItsData() {
        Cache<String, Object> owned = CacheKit.buildLocalIfAbsent(BIZ, 300);
        Cache<String, Object> replacement = new InMemoryCache(new HashMap<>());
        replacement.put("key", "external");
        CacheKit.register(BIZ, replacement);

        assertThat(CacheKit.buildLocalIfAbsent(BIZ, 300)).isNull();
        CacheKit.unregisterLocalIfOwned(BIZ, null);
        CacheKit.unregisterLocalIfOwned(BIZ, owned);
        assertThat(CacheKit.getCache(BIZ)).isSameAs(replacement);
        assertThat((Object) CacheKit.get(BIZ, "key")).isEqualTo("external");
    }

    /** \u9a8c\u8bc1<code>CacheKit.getCache(BIZ)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>owned</code>\uff0c<code>CacheKit.getCache(BIZ)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>loading</code>\uff0c<code>CacheKit.buildLocalIfAbsent(BIZ, 300)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void releasingOrdinaryCacheExposesAndBorrowsSameNameLoadingCache() {
        Cache<String, Object> owned = CacheKit.buildLocalIfAbsent(BIZ, 300);
        Cache<String, Object> loading = new InMemoryCache(new HashMap<>());
        CacheKit.registerLoading(BIZ, loading);
        assertThat(CacheKit.getCache(BIZ)).isSameAs(owned);

        CacheKit.unregisterLocalIfOwned(BIZ, owned);
        assertThat(CacheKit.getCache(BIZ)).isSameAs(loading);
        assertThat(CacheKit.buildLocalIfAbsent(BIZ, 300)).isNull();
        CacheKit.unregisterLocalIfOwned(BIZ, owned);
        assertThat(CacheKit.getCache(BIZ)).isSameAs(loading);
    }

    // ========================= Fixtures =========================

    /**
     * Minimal in-memory Cache used to test the {@code register()} path.
     */
    static class InMemoryCache implements io.ddd4j.core.cache.Cache<String, Object> {
        /** \u4fdd\u5b58\u6d4b\u8bd5\u6570\u636e\u7684\u5185\u5b58\u5b58\u50a8\u3002 */
        private final Map<String, Object> store;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>InMemoryCache</code> \u5b9e\u4f8b\u3002
         * @param store \u4fdd\u5b58\u6d4b\u8bd5\u6570\u636e\u7684\u5185\u5b58\u5b58\u50a8
         */
        InMemoryCache(Map<String, Object> store) {
            this.store = store;
        }

        /** {@inheritDoc} */
        @Override
        public Object getIfPresent(String key) {
            return store.get(key);
        }

        /** {@inheritDoc} */
        @Override
        public Object get(String key, java.util.function.Function<String, Object> mappingFunction) {
            return store.computeIfAbsent(key, mappingFunction);
        }

        /** {@inheritDoc} */
        @Override
        public void put(String key, Object value) {
            store.put(key, value);
        }

        /** {@inheritDoc} */
        @Override
        public void invalidate(String key) {
            store.remove(key);
        }

        /** {@inheritDoc} */
        @Override
        public void invalidateAll() {
            store.clear();
        }

        /** {@inheritDoc} */
        @Override
        public long estimatedSize() {
            return store.size();
        }

        /** {@inheritDoc} */
        @Override
        public CacheStats stats() {
            return null;
        }
    }
}
