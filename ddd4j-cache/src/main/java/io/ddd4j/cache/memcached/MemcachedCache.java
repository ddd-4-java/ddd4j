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
package io.ddd4j.cache.memcached;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cache.CASOperation;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheStats;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;
import net.rubyeye.xmemcached.MemcachedClient;

import java.util.Objects;
import java.util.function.Function;

/**
 * Memcached 缓存实现（基于 XMemcached 客户端）。
 *
 * <p>直接实现 ddd4j {@link Cache} SPI，不经 JetCache
 * （JetCache 无 Memcached 后端支持）。
 *
 * <p>特性：
 * <ul>
 *   <li>支持写后过期（{@code CacheConfig.expireAfterWriteSeconds}）</li>
 *   <li>值通过 Jackson 序列化为 JSON 存储</li>
 *   <li>不支持统计信息（{@link #stats()} 返回 null）</li>
 *   <li>不支持 {@code estimatedSize}（Memcached 协议无此能力，返回 -1）</li>
 * </ul>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class MemcachedCache<K, V> implements Cache<K, V> {

    /**
     * 用于访问 Memcached 服务器的客户端。
     */
    private final MemcachedClient memcachedClient;
    /**
     * 默认缓存存活时间，单位为秒。
     */
    private final int expireSeconds;
    /**
     * 反序列化缓存条目时使用的值类型。
     */
    private final Class<V> valueType;
    /**
     * 负责在 Redis 或 Memcached 中存储 JSON 值的映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造 Memcached 缓存。
     *
     * @param memcachedClient XMemcached 客户端
     * @param config          缓存配置（使用 expireAfterWriteSeconds 作为 Memcached 过期时间）
     * @param valueType       值类型（用于反序列化）
     * @param objectMapper    Jackson ObjectMapper
     */
    public MemcachedCache(MemcachedClient memcachedClient, CacheConfig config, Class<V> valueType, ObjectMapper objectMapper) {
        this.memcachedClient = Objects.requireNonNull(memcachedClient);
        this.expireSeconds = (int) (config.getExpireAfterWriteSeconds() > 0 ? config.getExpireAfterWriteSeconds() : 3600);
        this.valueType = Objects.requireNonNull(valueType);
        this.objectMapper = Objects.nonNull(objectMapper) ? objectMapper : JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
    }

    /**
     * 使用指定 ObjectMapper 工厂创建 Memcached 缓存。
     *
     * @param memcachedClient     Memcached 客户端
     * @param config              缓存配置
     * @param valueType           缓存值类型
     * @param objectMapperFactory ObjectMapper 工厂
     */
    public MemcachedCache(MemcachedClient memcachedClient, CacheConfig config, Class<V> valueType, Function<String, ObjectMapper> objectMapperFactory) {
        this(memcachedClient, config, valueType, objectMapperFactory.apply(valueType.getName()));
    }

    /**
     * 构造 Memcached 缓存（默认 ObjectMapper）。
     *
     * @param memcachedClient XMemcached 客户端
     * @param config          缓存配置
     * @param valueType       值类型
     */
    public MemcachedCache(MemcachedClient memcachedClient, CacheConfig config, Class<V> valueType) {
        this(memcachedClient, config, valueType, JsonMapper.builder().build());
    }

    /**
     * 将 Memcached 缓存键转换为字符串形式。
     *
     * @param key 缓存键
     * @return 写入 Memcached 使用的键
     */
    private String serializeKey(K key) {
        return String.valueOf(key);
    }

    /**
     * 读取现有缓存值。
     *
     * <p>此实现调用 <code>memcachedClient.get(serializeKey(key))</code>。</p>
     *
     * @param key 缓存键
     * @return 根据执行路径返回<code>null</code>、<code>objectMapper.readValue(json, valueType)</code>
     */
    @Override
    public V getIfPresent(K key) {
        try {
            String json = memcachedClient.get(serializeKey(key));
            if (StrKit.isBlank(json)) {
                return null;
            }
            return objectMapper.readValue(json, valueType);
        } catch (Exception e) {
            log.warn("Memcached 读取失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 读取调用指定的当前值。
     *
     * <p>此实现调用 <code>getIfPresent(key)</code>。</p>
     *
     * @param key             缓存键
     * @param mappingFunction 值加载函数
     * @return 实际返回<code>value</code>
     */
    @Override
    public V get(K key, Function<K, V> mappingFunction) {
        V value = getIfPresent(key);
        if (Objects.isNull(value)) {
            value = mappingFunction.apply(key);
            if (Objects.nonNull(value)) {
                put(key, value);
            }
        }
        return value;
    }

    /**
     * 将给定数据写入当前存储。
     *
     * <p>此实现调用 <code>objectMapper.writeValueAsString(value)</code>。</p>
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    @Override
    public void put(K key, V value) {
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            memcachedClient.set(serializeKey(key), expireSeconds, json);
        } catch (Exception e) {
            log.warn("Memcached 写入失败: key={}", key, e);
        }
    }

    /**
     * 使指定键的缓存条目失效。
     *
     * <p>此实现调用 <code>memcachedClient.delete(serializeKey(key))</code>。</p>
     *
     * @param key 缓存键
     */
    @Override
    public void invalidate(K key) {
        try {
            memcachedClient.delete(serializeKey(key));
        } catch (Exception e) {
            log.warn("Memcached 删除失败: key={}", key, e);
        }
    }

    /**
     * 保留后端现有键。调用方需通过明确的键逐个失效。
     */
    @Override
    public void invalidateAll() {
        log.warn("Memcached 不支持全局 invalidateAll（flushAll 危险操作），请通过明确的 key 逐个删除");
    }

    /**
     * 报告当前后端的缓存条目数量是否可用。
     *
     * @return 固定返回 -1，表示此适配器不估算条目数
     */
    @Override
    public long estimatedSize() {
        return -1;
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
     * 获取底层 XMemcached 客户端实例。
     *
     * @return MemcachedClient 实例
     */
    public MemcachedClient unwrap() {
        return memcachedClient;
    }

    /**
     * 按照适配器的值类型反序列化缓存文本。
     *
     * @param json 从缓存后端读出的字符串
     * @return 解码后的缓存值，输入为空或解码失败时返回空
     */
    @SuppressWarnings("unchecked")
    private V deserialize(String json) {
        if (StrKit.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, valueType);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== CAS SPI 实现（基于 xmemcached 原生 cas 版本号）====================

    /**
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>此实现调用 <code>serializeKey(key)</code>。</p>
     *
     * @param key           缓存键
     * @param expireSeconds 过期时间（秒），0 表示不修改 TTL
     * @param operation     CAS 操作回调（基于当前值计算新值）
     * @return 根据执行路径返回<code>null</code>、<code>newValue</code>
     */
    @Override
    @SuppressWarnings("unchecked")
    public V compareAndSet(K key, long expireSeconds, CASOperation<V, V> operation) {
        String cachedKey = serializeKey(key);
        int maxTries = operation.maxRetries() > 0 ? operation.maxRetries() : 16;
        for (int attempt = 0; attempt < maxTries; attempt++) {
            // 1. gets → 当前值 + cas 版本号
            net.rubyeye.xmemcached.GetsResponse<V> current;
            try {
                current = memcachedClient.gets(cachedKey);
            } catch (Exception e) {
                return null;
            }
            long currentCas = Objects.isNull(current) ? 0L : current.getCas();
            V currentValue = Objects.isNull(current) ? null : deserialize((String) current.getValue());

            // 2. 构造 ddd4j GetsResponse（version 仅 memcached 有真实值）
            io.ddd4j.core.cache.GetsResponse<V> response =
                    new io.ddd4j.core.cache.GetsResponse<V>() {
                        @Override
                        public String key() {
                            return cachedKey;
                        }

                        @Override
                        public V value() {
                            return currentValue;
                        }

                        @Override
                        public long version() {
                            return currentCas;
                        }
                    };

            // 3. 回调计算新值
            V newValue;
            try {
                newValue = operation.apply(response);
                if (Objects.isNull(newValue)) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }

            // 4. 序列化 + cas 写入
            String newJson;
            try {
                newJson = (newValue instanceof String) ? (String) newValue : objectMapper.writeValueAsString(newValue);
            } catch (Exception e) {
                return null;
            }
            try {
                int intExpire = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, expireSeconds));
                boolean ok = memcachedClient.cas(cachedKey, intExpire, newJson, currentCas);
                if (ok) return newValue;
                // 并发冲突：自动重试
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>此实现调用 <code>serializeKey(key)</code>。</p>
     *
     * @param key             缓存键
     * @param newValue        新值
     * @param expectedVersion 期望的版本号（来自 Memcached {@code gets} 返回的 cas 值）
     * @return 根据执行路径返回<code>false</code>、<code>memcachedClient.cas(cachedKey, 0, newJson, expectedVersion)</code>
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean compareAndSet(K key, V newValue, long expectedVersion) {
        String cachedKey = serializeKey(key);
        String newJson;
        try {
            newJson = (newValue instanceof String) ? (String) newValue : objectMapper.writeValueAsString(newValue);
        } catch (Exception e) {
            return false;
        }
        try {
            return memcachedClient.cas(cachedKey, 0, newJson, expectedVersion);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>此实现调用 <code>serializeKey(key)</code>。</p>
     *
     * @param key              缓存键
     * @param expectedOldValue 期望的旧值（null 表示仅当 key 不存在时写入 = putIfAbsent）
     * @param newValue         新值
     * @return 根据执行路径返回<code>memcachedClient.add(cachedKey, expireSeconds, newJson)</code>、<code>false</code>、<code>memcachedClient.cas(cachedKey, 0, newJson, current.getCas())</code>
     */
    @Override
    public boolean compareAndSet(K key, V expectedOldValue, V newValue) {
        // Memcached 的 CAS 基于版本号而非值比较；这里降级为 gets + 版本号 cas
        String cachedKey = serializeKey(key);
        try {
            net.rubyeye.xmemcached.GetsResponse<V> current = memcachedClient.gets(cachedKey);
            if (Objects.isNull(current)) {
                // key 不存在：expectedOldValue 为 null 时等价于 add
                if (Objects.isNull(expectedOldValue)) {
                    String newJson = (newValue instanceof String) ? (String) newValue : objectMapper.writeValueAsString(newValue);
                    return memcachedClient.add(cachedKey, expireSeconds, newJson);
                }
                return false;
            }
            V currentValue = deserialize((String) current.getValue());
            // 值比较（注意：序列化后的 JSON 字符串比较，而非对象 equals）
            String expectedJson = Objects.isNull(expectedOldValue) ? null
                    : (expectedOldValue instanceof String ? (String) expectedOldValue : objectMapper.writeValueAsString(expectedOldValue));
            String currentJson = (String) current.getValue();
            if (Objects.equals(expectedJson, currentJson)) {
                String newJson = (newValue instanceof String) ? (String) newValue : objectMapper.writeValueAsString(newValue);
                return memcachedClient.cas(cachedKey, 0, newJson, current.getCas());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
