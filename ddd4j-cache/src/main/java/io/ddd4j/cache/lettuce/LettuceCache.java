package io.ddd4j.cache.lettuce;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheStats;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * Lettuce 原生缓存实现。
 *
 * <p>传入 Lettuce {@link RedisCommands}（同步命令接口）实例，
 * 直接实现 ddd4j {@link Cache} SPI。值通过 Jackson 序列化为 JSON 字符串存储到 Redis。
 *
 * <p>使用示例：
 * <pre>{@code
 *   RedisClient client = RedisClient.create("redis://localhost:6379");
 *   StatefulRedisConnection<String, String> conn = client.connect();
 *   RedisCommands<String, String> commands = conn.sync();
 *   CacheConfig config = CacheConfig.builder("user").expireAfterWriteSeconds(300).build();
 *   Cache<String, User> cache = new LettuceCache<>(commands, config, User.class);
 *   cache.put("123", user);
 *   User cached = cache.getIfPresent("123");
 * }</pre>
 *
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class LettuceCache<V> implements Cache<String, V> {

    private final RedisCommands<String, String> commands;
    private final Duration expireDuration;
    private final Class<V> valueType;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    /**
     * 构造 Lettuce 缓存。
     *
     * @param commands     Lettuce 同步字符串命令接口
     * @param config       缓存配置
     * @param valueType    值类型
     * @param objectMapper Jackson ObjectMapper
     */
    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType, ObjectMapper objectMapper) {
        this.commands = Objects.requireNonNull(commands);
        this.expireDuration = config.getExpireAfterWriteSeconds() > 0 ? Duration.ofSeconds(config.getExpireAfterWriteSeconds()) : Duration.ZERO;
        this.valueType = Objects.requireNonNull(valueType);
        this.keyPrefix = config.getName() + ":";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType, Function<String, ObjectMapper> objectMapperFactory) {
        this(commands, config, valueType, objectMapperFactory.apply(valueType.getName()));
    }

    /**
     * 构造 Lettuce 缓存（默认 ObjectMapper）。
     */
    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType) {
        this(commands, config, valueType, new ObjectMapper());
    }

    private String key(String key) {
        return keyPrefix + key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getIfPresent(String key) {
        try {
            String json = commands.get(key(key));
            if (json == null) return null;
            if (valueType == String.class) return (V) json;
            return objectMapper.readValue(json, valueType);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public V get(String key, Function<String, V> mappingFunction) {
        V value = getIfPresent(key);
        if (value == null) {
            value = mappingFunction.apply(key);
            if (value != null) put(key, value);
        }
        return value;
    }

    @Override
    public void put(String key, V value) {
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            if (!expireDuration.isZero()) {
                commands.setex(key(key), expireDuration.getSeconds(), json);
            } else {
                commands.set(key(key), json);
            }
        } catch (Exception e) {
            // 写入失败忽略
        }
    }

    @Override
    public void invalidate(String key) {
        try {
            commands.del(key(key));
        } catch (Exception e) {
            // 删除失败忽略
        }
    }

    @Override
    public void invalidateAll() {
        // Redis 不建议 flushAll，需通过 key 前缀逐个删除
    }

    @Override
    public long estimatedSize() {
        return -1;
    }

    @Override
    public CacheStats stats() {
        return null;
    }

}
