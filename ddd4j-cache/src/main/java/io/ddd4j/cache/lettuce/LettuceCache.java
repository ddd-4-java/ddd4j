package io.ddd4j.cache.lettuce;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cache.CASOperation;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cache.CacheConfig;
import io.ddd4j.core.cache.CacheStats;
import io.ddd4j.kit.lang.StrKit;
import io.lettuce.core.ScriptOutputType;
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

    private static final String LUA_CAS =
            "local cur = redis.call('GET', KEYS[1]) " +
                    "if cur == ARGV[1] then " +
                    "  redis.call('SET', KEYS[1], ARGV[2]) " +
                    "  if tonumber(ARGV[3]) > 0 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end " +
                    "  return 1 " +
                    "end " +
                    "return 0";
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
        this.objectMapper = Objects.nonNull(objectMapper) ? objectMapper : JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
    }

    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType, Function<String, ObjectMapper> objectMapperFactory) {
        this(commands, config, valueType, objectMapperFactory.apply(valueType.getName()));
    }

    /**
     * 构造 Lettuce 缓存（默认 ObjectMapper）。
     */
    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType) {
        this(commands, config, valueType, JsonMapper.builder().build());
    }

    private String key(String key) {
        return keyPrefix + key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getIfPresent(String key) {
        try {
            String json = commands.get(key(key));
            if (Objects.isNull(json)) {
                return null;
            }
            if (valueType == String.class) {
                return (V) json;
            }
            return objectMapper.readValue(json, valueType);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public V get(String key, Function<String, V> mappingFunction) {
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

    // ==================== CAS SPI 实现（基于 Redis Lua 脚本原子操作）====================

    @Override
    public CacheStats stats() {
        return null;
    }

    @Override
    public V compareAndSet(String key, long expireSeconds, CASOperation<V, V> operation) {
        String cachedKey = key(key);
        int maxTries = operation.maxRetries() > 0 ? operation.maxRetries() : 16;
        for (int attempt = 0; attempt < maxTries; attempt++) {
            String currentJson = commands.get(cachedKey);
            V currentValue = deserialize(currentJson);
            io.ddd4j.core.cache.GetsResponse<V> resp = new io.ddd4j.core.cache.GetsResponse<>() {
                @Override
                public String key() {
                    return cachedKey;
                }

                @Override
                public V value() {
                    return currentValue;
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
            String newJson = serialize(newValue);
            String result = commands.eval(LUA_CAS, ScriptOutputType.INTEGER,
                    new String[]{cachedKey},
                    Objects.isNull(currentJson) ? "" : currentJson, newJson, String.valueOf(expireSeconds));
            if ("1".equals(result)) {
                return newValue;
            }
        }
        return null;
    }

    @Override
    public boolean compareAndSet(String key, V expectedOldValue, V newValue) {
        String cachedKey = key(key);
        String expectedJson = Objects.isNull(expectedOldValue) ? "" : serialize(expectedOldValue);
        String newJson = serialize(newValue);
        String result = commands.eval(LUA_CAS, ScriptOutputType.INTEGER, new String[]{cachedKey}, expectedJson, newJson, "0");
        return "1".equals(result);
    }

    private String serialize(V value) {
        if (Objects.isNull(value)) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "";
        }
    }

    private V deserialize(String json) {
        if (StrKit.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, valueType);
        } catch (Exception e) {
            return null;
        }
    }
}
