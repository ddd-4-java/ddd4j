package io.ddd4j.cache.memcached;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper()
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
        if (value == null) {
            value = mappingFunction.apply(key);
            if (value != null) {
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

}
