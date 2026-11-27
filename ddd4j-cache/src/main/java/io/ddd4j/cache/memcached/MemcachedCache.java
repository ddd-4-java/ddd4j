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

    /** \u7528\u4e8e\u8bbf\u95ee Memcached \u670d\u52a1\u5668\u7684\u5ba2\u6237\u7aef\u3002 */
    private final MemcachedClient memcachedClient;
    /** \u9ed8\u8ba4\u7f13\u5b58\u5b58\u6d3b\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u79d2\u3002 */
    private final int expireSeconds;
    /** \u53cd\u5e8f\u5217\u5316\u7f13\u5b58\u6761\u76ee\u65f6\u4f7f\u7528\u7684\u503c\u7c7b\u578b\u3002 */
    private final Class<V> valueType;
    /** \u8d1f\u8d23\u5728 Redis \u6216 Memcached \u4e2d\u5b58\u50a8 JSON \u503c\u7684\u6620\u5c04\u5668\u3002 */
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
     * \u5c06 Memcached \u7f13\u5b58\u952e\u8f6c\u6362\u4e3a\u5b57\u7b26\u4e32\u5f62\u5f0f\u3002
     * @param key \u7f13\u5b58\u952e
     * @return \u5199\u5165 Memcached \u4f7f\u7528\u7684\u952e
     */
    private String serializeKey(K key) {
        return String.valueOf(key);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void put(K key, V value) {
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            memcachedClient.set(serializeKey(key), expireSeconds, json);
        } catch (Exception e) {
            log.warn("Memcached 写入失败: key={}", key, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void invalidate(K key) {
        try {
            memcachedClient.delete(serializeKey(key));
        } catch (Exception e) {
            log.warn("Memcached 删除失败: key={}", key, e);
        }
    }

    /** \u4fdd\u7559\u540e\u7aef\u73b0\u6709\u952e\u3002\u8c03\u7528\u65b9\u9700\u901a\u8fc7\u660e\u786e\u7684\u952e\u9010\u4e2a\u5931\u6548\u3002 */
    @Override
    public void invalidateAll() {
        log.warn("Memcached 不支持全局 invalidateAll（flushAll 危险操作），请通过明确的 key 逐个删除");
    }

    /**
     * \u62a5\u544a\u5f53\u524d\u540e\u7aef\u7684\u7f13\u5b58\u6761\u76ee\u6570\u91cf\u662f\u5426\u53ef\u7528\u3002
     * @return \u56fa\u5b9a\u8fd4\u56de -1\uff0c\u8868\u793a\u6b64\u9002\u914d\u5668\u4e0d\u4f30\u7b97\u6761\u76ee\u6570
     */
    @Override
    public long estimatedSize() {
        return -1;
    }

    /** {@inheritDoc} */
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
     * \u6309\u7167\u9002\u914d\u5668\u7684\u503c\u7c7b\u578b\u53cd\u5e8f\u5217\u5316\u7f13\u5b58\u6587\u672c\u3002
     * @param json \u4ece\u7f13\u5b58\u540e\u7aef\u8bfb\u51fa\u7684\u5b57\u7b26\u4e32
     * @return \u89e3\u7801\u540e\u7684\u7f13\u5b58\u503c\uff0c\u8f93\u5165\u4e3a\u7a7a\u6216\u89e3\u7801\u5931\u8d25\u65f6\u8fd4\u56de\u7a7a
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
