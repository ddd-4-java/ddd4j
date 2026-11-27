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

    /**
     * 测试期间注册及清理的业务缓存名称。
     */
    private static final String BIZ = "cache-kit-test";

    /**
     * 清理同名缓存并把本地缓存实现恢复为 Caffeine。
     */
    @BeforeEach
    void setUp() {
        CacheKit.unregister(BIZ);
        CacheKit.setDefaultType(LocalCacheType.CAFFEINE);
    }

    /**
     * 注销当前用例使用的业务缓存。
     */
    @AfterEach
    void tearDown() {
        CacheKit.unregister(BIZ);
    }

    /**
     * 验证<code>CacheKit.getCacheNames()</code> 包含 <code>BIZ</code>。
     */
    @Test
    void build_shouldCreateLocalCache() {
        CacheKit.build(BIZ, 300);

        assertThat(CacheKit.getCacheNames()).contains(BIZ);
    }

    /**
     * 验证<code>CacheKit.getCache(BIZ)</code>非空。
     */
    @Test
    void build_withBuilder_shouldCreateLocalCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10).expireAfterWriteSeconds(60));

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
    }

    /**
     * 验证<code>CacheKit.getCache(BIZ)</code>非空，<code>CacheKit.getCacheNames()</code> 包含 <code>BIZ</code>。
     */
    @Test
    void build_withGuavaType_shouldCreateGuavaCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.GUAVA);

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
        assertThat(CacheKit.getCacheNames()).contains(BIZ);
    }

    /**
     * 验证<code>CacheKit.getCache(BIZ)</code>非空。
     */
    @Test
    void build_withHutoolType_shouldCreateHutoolCache() {
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.HUTOOL);

        assertThat(CacheKit.getCache(BIZ)).isNotNull();
    }

    /**
     * 验证<code>(String) CacheKit.get(BIZ, "k1")</code> 等于 <code>"v1"</code>。
     */
    @Test
    void putAndGet_shouldStoreAndRetrieveValue() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");

        assertThat((String) CacheKit.get(BIZ, "k1")).isEqualTo("v1");
    }

    /**
     * 验证<code>(Object) CacheKit.get(BIZ, "missing")</code>为空。
     */
    @Test
    void get_shouldReturnNullForMissingKey() {
        CacheKit.build(BIZ, 300);

        assertThat((Object) CacheKit.get(BIZ, "missing")).isNull();
    }

    /**
     * 验证<code>(Object) CacheKit.get("non-existent-biz", "k")</code>为空。
     */
    @Test
    void get_shouldReturnNullWhenBizNotRegistered() {
        assertThat((Object) CacheKit.get("non-existent-biz", "k")).isNull();
    }

    /**
     * 验证<code>value</code> 等于 <code>"loaded"</code>，<code>loadCount.get()</code> 等于 <code>1</code>。
     */
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

    /**
     * 验证<code>(Object) CacheKit.get(BIZ, "k1")</code>为空，<code>(String) CacheKit.get(BIZ, "k2")</code> 等于 <code>"v2"</code>。
     */
    @Test
    void invalidate_shouldRemoveSingleKey() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.put(BIZ, "k2", "v2");

        CacheKit.invalidate(BIZ, "k1");

        assertThat((Object) CacheKit.get(BIZ, "k1")).isNull();
        assertThat((String) CacheKit.get(BIZ, "k2")).isEqualTo("v2");
    }

    /**
     * 验证<code>(Object) CacheKit.get(BIZ, "k1")</code>为空，<code>(Object) CacheKit.get(BIZ, "k2")</code>为空。
     */
    @Test
    void invalidateAll_shouldClearAllKeys() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.put(BIZ, "k2", "v2");

        CacheKit.invalidateAll(BIZ);

        assertThat((Object) CacheKit.get(BIZ, "k1")).isNull();
        assertThat((Object) CacheKit.get(BIZ, "k2")).isNull();
    }

    /**
     * 验证<code>CacheKit.exists(BIZ, "k1")</code>为真，<code>CacheKit.exists(BIZ, "missing")</code>为假，<code>CacheKit.exists("non-existent-biz", "k1")</code>为假。
     */
    @Test
    void exists_shouldReportPresence() {
        CacheKit.build(BIZ, 300);
        CacheKit.put(BIZ, "k1", "v1");

        assertThat(CacheKit.exists(BIZ, "k1")).isTrue();
        assertThat(CacheKit.exists(BIZ, "missing")).isFalse();
        assertThat(CacheKit.exists("non-existent-biz", "k1")).isFalse();
    }

    /**
     * 验证<code>stats</code>非空，<code>stats.hitCount()</code> 大于或等于 <code>1</code>。
     */
    @Test
    void getStats_shouldReturnNonNullWhenRecordStatsEnabled() {
        CacheKit.build(BIZ, b -> b.maximumSize(10).recordStats(true));
        CacheKit.put(BIZ, "k1", "v1");
        CacheKit.get(BIZ, "k1");

        CacheStats stats = CacheKit.getStats(BIZ);

        assertThat(stats).isNotNull();
        assertThat(stats.hitCount()).isGreaterThanOrEqualTo(1);
    }

    /**
     * 验证<code>CacheKit.getCacheNames()</code> 包含 <code>BIZ</code>，<code>CacheKit.getCache(BIZ)</code> 指向同一实例 <code>cache</code>。
     */
    @Test
    void register_shouldRegisterExternalCache() {
        Map<String, Object> store = new HashMap<>();
        InMemoryCache cache = new InMemoryCache(store);

        CacheKit.register(BIZ, cache);

        assertThat(CacheKit.getCacheNames()).contains(BIZ);
        assertThat(CacheKit.getCache(BIZ)).isSameAs(cache);
    }

    /**
     * 验证<code>CacheKit.register(null, new InMemoryCache(new HashMap&lt;&gt;()))</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void register_shouldRejectNullBiz() {
        assertThatThrownBy(() -> CacheKit.register(null, new InMemoryCache(new HashMap<>())))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * 验证<code>CacheKit.register(BIZ, null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void register_shouldRejectNullCache() {
        assertThatThrownBy(() -> CacheKit.register(BIZ, null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * 验证<code>CacheKit.getCacheNames()</code> 包含 <code>BIZ</code>，<code>CacheKit.getCacheNames()</code> 不包含 <code>BIZ</code>。
     */
    @Test
    void unregister_shouldRemoveCache() {
        CacheKit.build(BIZ, 300);
        assertThat(CacheKit.getCacheNames()).contains(BIZ);

        CacheKit.unregister(BIZ);

        assertThat(CacheKit.getCacheNames()).doesNotContain(BIZ);
    }

    /**
     * 验证<code>value</code> 等于 <code>"auto-k1"</code>。
     */
    @Test
    void buildWithLoader_shouldAutoLoadOnGet() {
        CacheKit.buildWithLoader(BIZ, key -> "auto-" + key,
                b -> b.maximumSize(10).expireAfterWriteSeconds(60));

        String value = CacheKit.get(BIZ, "k1");

        assertThat(value).isEqualTo("auto-k1");
    }

    /**
     * 验证<code>CacheKit.putIfAbsent(BIZ, "k1", "v1")</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void putIfAbsent_shouldRejectWhenCacheNotCasCapable() {
        // Hutool cache does not implement CasCache
        CacheKit.build(BIZ, b -> b.maximumSize(10), LocalCacheType.HUTOOL);

        assertThatThrownBy(() -> CacheKit.putIfAbsent(BIZ, "k1", "v1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证<code>CacheKit.getDefaultType()</code> 等于 <code>LocalCacheType.GUAVA</code>。
     */
    @Test
    void setDefaultType_shouldUpdateDefaultType() {
        CacheKit.setDefaultType(LocalCacheType.GUAVA);

        assertThat(CacheKit.getDefaultType()).isEqualTo(LocalCacheType.GUAVA);
    }

    /**
     * 验证<code>owners</code> 的大小为 <code>1</code>，<code>CacheKit.getCache(BIZ)</code> 指向同一实例 <code>owners.get(0)</code>。
     *
     * @throws Exception 等待并发操作时失败
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

    /**
     * 验证<code>CacheKit.buildLocalIfAbsent(BIZ, 300)</code>为空，<code>CacheKit.getCache(BIZ)</code> 指向同一实例 <code>replacement</code>，<code>(Object) CacheKit.get(BIZ, "key")</code> 等于 <code>"external"</code>。
     */
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

    /**
     * 验证<code>CacheKit.getCache(BIZ)</code> 指向同一实例 <code>owned</code>，<code>CacheKit.getCache(BIZ)</code> 指向同一实例 <code>loading</code>，<code>CacheKit.buildLocalIfAbsent(BIZ, 300)</code>为空。
     */
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
        /**
         * 保存测试数据的内存存储。
         */
        private final Map<String, Object> store;

        /**
         * 用给定值初始化 <code>InMemoryCache</code> 实例。
         *
         * @param store 保存测试数据的内存存储
         */
        InMemoryCache(Map<String, Object> store) {
            this.store = store;
        }

        /**
         * 读取现有缓存值。
         *
         * <p>测试夹具会调用 <code>store.get(key)</code>。</p>
         *
         * @param key 缓存键
         * @return 实际返回<code>store.get(key)</code>
         */
        @Override
        public Object getIfPresent(String key) {
            return store.get(key);
        }

        /**
         * 读取调用指定的当前值。
         *
         * <p>测试夹具会调用 <code>store.computeIfAbsent(key, mappingFunction)</code>。</p>
         *
         * @param key             缓存键
         * @param mappingFunction 值加载函数
         * @return 实际返回<code>store.computeIfAbsent(key, mappingFunction)</code>
         */
        @Override
        public Object get(String key, java.util.function.Function<String, Object> mappingFunction) {
            return store.computeIfAbsent(key, mappingFunction);
        }

        /**
         * 将给定数据写入当前存储。
         *
         * <p>测试夹具会调用 <code>store.put(key, value)</code>。</p>
         *
         * @param key   缓存键
         * @param value 缓存值
         */
        @Override
        public void put(String key, Object value) {
            store.put(key, value);
        }

        /**
         * 使指定键的缓存条目失效。
         *
         * <p>测试夹具会调用 <code>store.remove(key)</code>。</p>
         *
         * @param key 缓存键
         */
        @Override
        public void invalidate(String key) {
            store.remove(key);
        }

        /**
         * 使当前缓存中的全部条目失效。
         *
         * <p>测试夹具会调用 <code>store.clear()</code>。</p>
         */
        @Override
        public void invalidateAll() {
            store.clear();
        }

        /**
         * 返回当前缓存条目规模的估算值。
         *
         * <p>测试夹具会调用 <code>store.size()</code>。</p>
         *
         * @return 实际返回<code>store.size()</code>
         */
        @Override
        public long estimatedSize() {
            return store.size();
        }

        /**
         * 返回当前缓存实现记录的统计信息。
         *
         * @return 此实现返回<code>null</code>
         */
        @Override
        public CacheStats stats() {
            return null;
        }
    }
}
