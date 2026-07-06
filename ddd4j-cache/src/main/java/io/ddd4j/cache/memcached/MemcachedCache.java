package io.ddd4j.cache.memcached;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheStats;
import io.ddd4j.core.cache.CASOperation;
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

    private final MemcachedClient memcachedClient;
    private final int expireSeconds;
    private final Class<V> valueType;
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
        this.objectMapper = Objects.nonNull(objectMapper) ? objectMapper : new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

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
        this(memcachedClient, config, valueType, new ObjectMapper());
    }

    private String serializeKey(K key) {
        return String.valueOf(key);
    }

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

    @Override
    public void put(K key, V value) {
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            memcachedClient.set(serializeKey(key), expireSeconds, json);
        } catch (Exception e) {
            log.warn("Memcached 写入失败: key={}", key, e);
        }
    }

    @Override
    public void invalidate(K key) {
        try {
            memcachedClient.delete(serializeKey(key));
        } catch (Exception e) {
            log.warn("Memcached 删除失败: key={}", key, e);
        }
    }

    @Override
    public void invalidateAll() {
        log.warn("Memcached 不支持全局 invalidateAll（flushAll 危险操作），请通过明确的 key 逐个删除");
    }

    @Override
    public long estimatedSize() {
        return -1;
    }

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

    @SuppressWarnings("unchecked")
    private V deserialize(String json) {
        if (StrKit.isBlank(json)) return null;
        try { return objectMapper.readValue(json, valueType); } catch (Exception e) { return null; }
    }

    // ==================== CAS SPI 实现（基于 xmemcached 原生 cas 版本号）====================

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
            long currentCas = current == null ? 0L : current.getCas();
            V currentValue = current == null ? null : deserialize((String) current.getValue());

            // 2. 构造 ddd4j GetsResponse（version 仅 memcached 有真实值）
            io.ddd4j.core.cache.GetsResponse<V> response =
                    new io.ddd4j.core.cache.GetsResponse<>() {
                        @Override public String key() { return cachedKey; }
                        @Override public V value() { return currentValue; }
                        @Override public long version() { return currentCas; }
                    };

            // 3. 回调计算新值
            V newValue;
            try {
                newValue = operation.apply(response);
                if (newValue == null) return null;
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

    @Override
    public boolean compareAndSet(K key, V expectedOldValue, V newValue) {
        // Memcached 的 CAS 基于版本号而非值比较；这里降级为 gets + 版本号 cas
        String cachedKey = serializeKey(key);
        try {
            net.rubyeye.xmemcached.GetsResponse<V> current = memcachedClient.gets(cachedKey);
            if (current == null) {
                // key 不存在：expectedOldValue 为 null 时等价于 add
                if (expectedOldValue == null) {
                    String newJson = (newValue instanceof String) ? (String) newValue : objectMapper.writeValueAsString(newValue);
                    return memcachedClient.add(cachedKey, expireSeconds, newJson);
                }
                return false;
            }
            V currentValue = deserialize((String) current.getValue());
            // 值比较（注意：序列化后的 JSON 字符串比较，而非对象 equals）
            String expectedJson = expectedOldValue == null ? null
                    : (expectedOldValue instanceof String ? (String) expectedOldValue : objectMapper.writeValueAsString(expectedOldValue));
            String currentJson = (String) current.getValue();
            if (java.util.Objects.equals(expectedJson, currentJson)) {
                String newJson = (newValue instanceof String) ? (String) newValue : objectMapper.writeValueAsString(newValue);
                return memcachedClient.cas(cachedKey, 0, newJson, current.getCas());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
