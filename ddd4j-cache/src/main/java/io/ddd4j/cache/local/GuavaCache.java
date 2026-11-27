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

import com.google.common.cache.CacheBuilder;
import io.ddd4j.core.cache.CASOperation;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheStats;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Guava 本地缓存实现。
 *
 * <p>基于 Google Guava Cache，支持：写后过期、访问后过期、最大容量。
 * 不支持统计信息映射（{@link #stats()} 返回 null）。
 *
 * <p>从 ddd4j-kit/cache/impl/GuavaCache 迁移，改包名并适配新的 {@link CacheConfig}。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class GuavaCache<K, V> implements Cache<K, V> {

    /**
     * 保存本地缓存条目的 Guava 缓存。
     */
    private final com.google.common.cache.Cache<K, V> cache;

    /**
     * 封装已构造的 Guava 本地缓存。
     *
     * @param cache 用于存放键值的底层缓存
     */
    private GuavaCache(com.google.common.cache.Cache<K, V> cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    /**
     * 根据 {@link CacheConfig} 创建 Guava 缓存实例。
     *
     * @param config 缓存配置
     * @param <K>    键类型
     * @param <V>    值类型
     * @return GuavaCache 实例
     */
    public static <K, V> GuavaCache<K, V> create(CacheConfig config) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
        if (config.getMaximumSize() > 0) {
            builder.maximumSize(config.getMaximumSize());
        }
        if (config.getExpireAfterWriteSeconds() > 0) {
            builder.expireAfterWrite(Duration.ofSeconds(config.getExpireAfterWriteSeconds()));
        }
        if (config.getExpireAfterAccessSeconds() > 0) {
            builder.expireAfterAccess(Duration.ofSeconds(config.getExpireAfterAccessSeconds()));
        }
        return new GuavaCache<>(builder.build());
    }

    /**
     * 读取现有缓存值。
     *
     * <p>此实现调用 <code>cache.getIfPresent(key)</code>。</p>
     *
     * @param key 缓存键
     * @return 实际返回<code>cache.getIfPresent(key)</code>
     */
    @Override
    public V getIfPresent(K key) {
        return cache.getIfPresent(key);
    }

    /**
     * 读取调用指定的当前值。
     *
     * @param key             缓存键
     * @param mappingFunction 值加载函数
     * @return 缓存值
     */
    @Override
    public V get(K key, Function<K, V> mappingFunction) {
        try {
            return cache.get(key, () -> mappingFunction.apply(key));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将给定数据写入当前存储。
     *
     * <p>此实现调用 <code>cache.put(key, value)</code>。</p>
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    /**
     * 批量设置缓存值。
     *
     * <p>此实现调用 <code>cache.putAll(map)</code>。</p>
     *
     * @param map 缓存键值对
     */
    @Override
    public void putAll(Map<K, V> map) {
        cache.putAll(map);
    }

    /**
     * 使指定键的缓存条目失效。
     *
     * <p>此实现调用 <code>cache.invalidate(key)</code>。</p>
     *
     * @param key 缓存键
     */
    @Override
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    /**
     * 使当前缓存中的全部条目失效。
     *
     * <p>此实现调用 <code>cache.invalidateAll()</code>。</p>
     */
    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 使当前缓存中的全部条目失效。
     *
     * <p>此实现调用 <code>cache.invalidateAll(keys)</code>。</p>
     *
     * @param keys 缓存键集合
     */
    @Override
    public void invalidateAll(Iterable<K> keys) {
        cache.invalidateAll(keys);
    }

    /**
     * 返回当前缓存条目规模的估算值。
     *
     * <p>此实现调用 <code>cache.size()</code>。</p>
     *
     * @return 实际返回<code>cache.size()</code>
     */
    @Override
    public long estimatedSize() {
        return cache.size();
    }

    /**
     * 返回当前缓存实现记录的统计信息。
     *
     * @return 此实现返回<code>null</code>
     */
    @Override
    public CacheStats stats() {
        // Guava 统计信息不映射到 ddd4j CacheStats
        return null;
    }

    // ==================== CAS 实现（基于 asMap().replace + 重试）====================

    /**
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>此实现调用 <code>cache.asMap()</code>。</p>
     *
     * @param key           缓存键
     * @param expireSeconds 过期时间（秒），0 表示不修改 TTL
     * @param operation     CAS 操作回调（基于当前值计算新值）
     * @return 根据执行路径返回<code>null</code>、<code>newValue</code>
     */
    @Override
    public V compareAndSet(K key, long expireSeconds, CASOperation<V, V> operation) {
        ConcurrentMap<K, V> map = cache.asMap();
        for (int attempt = 0; attempt < 16; attempt++) {
            V current = cache.getIfPresent(key);
            io.ddd4j.core.cache.GetsResponse<V> resp = new io.ddd4j.core.cache.GetsResponse<>() {
                @Override
                public String key() {
                    return String.valueOf(key);
                }

                @Override
                public V value() {
                    return current;
                }
            };
            V newValue;
            try {
                newValue = operation.apply(resp);
                if (Objects.isNull(newValue)) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
            if (Objects.isNull(current)) {
                if (Objects.isNull(map.putIfAbsent(key, newValue))) {
                    return newValue;
                }
                continue;
            }
            if (map.replace(key, current, newValue)) {
                return newValue;
            }
        }
        return null;
    }

    /**
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>此实现调用 <code>cache.asMap()</code>。</p>
     *
     * @param key              缓存键
     * @param expectedOldValue 期望的旧值（null 表示仅当 key 不存在时写入 = putIfAbsent）
     * @param newValue         新值
     * @return 根据执行路径返回<code>Objects.isNull(map.putIfAbsent(key, newValue))</code>、<code>map.replace(key, expectedOldValue, newValue)</code>
     */
    @Override
    public boolean compareAndSet(K key, V expectedOldValue, V newValue) {
        ConcurrentMap<K, V> map = cache.asMap();
        if (Objects.isNull(expectedOldValue)) {
            return Objects.isNull(map.putIfAbsent(key, newValue));
        }
        return map.replace(key, expectedOldValue, newValue);
    }

    /**
     * 获取底层 Guava 缓存实例。
     *
     * @return Guava Cache 实例
     */
    public com.google.common.cache.Cache<K, V> unwrap() {
        return cache;
    }

}
