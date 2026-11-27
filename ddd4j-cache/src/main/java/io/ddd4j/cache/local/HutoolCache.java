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

import cn.hutool.cache.CacheUtil;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheStats;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Hutool 本地缓存实现。
 *
 * <p>基于 Hutool {@link cn.hutool.cache.impl.TimedCache}，仅支持写后过期（定时清理）。
 * 轻量级实现，不支持统计信息（{@link #stats()} 返回 null）。
 *
 * <p>从 ddd4j-kit/cache/impl/HutoolCache 迁移，改包名并适配新的 {@link CacheConfig}。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class HutoolCache<K, V> implements Cache<K, V> {

    /** \u4fdd\u5b58\u672c\u5730\u7f13\u5b58\u6761\u76ee\u7684 Hutool \u7f13\u5b58\u3002 */
    private final cn.hutool.cache.impl.TimedCache<K, V> cache;

    /**
     * \u5c01\u88c5\u5df2\u6784\u9020\u7684 Hutool \u672c\u5730\u7f13\u5b58\u3002
     * @param cache \u7528\u4e8e\u5b58\u653e\u952e\u503c\u7684\u5e95\u5c42\u7f13\u5b58
     */
    private HutoolCache(cn.hutool.cache.impl.TimedCache<K, V> cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    /**
     * 根据 {@link CacheConfig} 创建 Hutool 缓存实例。
     *
     * <p>仅使用 {@link CacheConfig#getExpireAfterWriteSeconds()} 作为过期时间，
     * 其他配置项（最大容量、刷新策略等）不适用。
     *
     * @param config 缓存配置
     * @param <K>    键类型
     * @param <V>    值类型
     * @return HutoolCache 实例
     */
    public static <K, V> HutoolCache<K, V> create(CacheConfig config) {
        long expiredMs = (config.getExpireAfterWriteSeconds() > 0 ? config.getExpireAfterWriteSeconds() : 3600) * 1000;
        cn.hutool.cache.impl.TimedCache<K, V> timed = CacheUtil.newTimedCache(expiredMs);
        timed.schedulePrune(5000);
        return new HutoolCache<>(timed);
    }

    /**
     * 读取现有缓存值。
     *
     * <p>此实现调用 <code>cache.get(key)</code>。</p>
     *
     * @param key 缓存键
     * @return 实际返回<code>cache.get(key)</code>
     */
    @Override
    public V getIfPresent(K key) {
        return cache.get(key);
    }

    /**
     * 读取调用指定的当前值。
     *
     * <p>此实现调用 <code>cache.get(key)</code>。</p>
     *
     * @param key 缓存键
     * @param mappingFunction 值加载函数
     * @return 实际返回<code>value</code>
     */
    @Override
    public V get(K key, Function<K, V> mappingFunction) {
        V value = cache.get(key);
        if (Objects.isNull(value)) {
            value = mappingFunction.apply(key);
            if (Objects.nonNull(value)) {
                cache.put(key, value);
            }
        }
        return value;
    }

    /**
     * 将给定数据写入当前存储。
     *
     * <p>此实现调用 <code>cache.put(key, value)</code>。</p>
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    /**
     * 批量设置缓存值。
     *
     * @param map 缓存键值对
     */
    @Override
    public void putAll(Map<K, V> map) {
        map.forEach(this::put);
    }

    /**
     * 使指定键的缓存条目失效。
     *
     * <p>此实现调用 <code>cache.remove(key)</code>。</p>
     *
     * @param key 缓存键
     */
    @Override
    public void invalidate(K key) {
        cache.remove(key);
    }

    /**
     * 使当前缓存中的全部条目失效。
     *
     * <p>此实现调用 <code>cache.clear()</code>。</p>
     */
    @Override
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * 使当前缓存中的全部条目失效。
     *
     * @param keys 缓存键集合
     */
    @Override
    public void invalidateAll(Iterable<K> keys) {
        keys.forEach(this::invalidate);
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
        return null;
    }

    /**
     * 获取底层 Hutool 缓存实例。
     *
     * @return Hutool TimedCache 实例
     */
    public cn.hutool.cache.impl.TimedCache<K, V> unwrap() {
        return cache;
    }

}
