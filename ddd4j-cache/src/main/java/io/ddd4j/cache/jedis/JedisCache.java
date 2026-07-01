package io.ddd4j.cache.jedis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cache.*;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

import java.util.Objects;
import java.util.function.Function;

/**
 * Jedis 原生缓存实现（Jedis 5+ / 7+，基于 {@link UnifiedJedis} 统一接口）。
 *
 * <p>传入 {@link UnifiedJedis} 实例（如 {@link redis.clients.jedis.RedisClient} 或
 * {@link redis.clients.jedis.JedisPooled}），直接实现 ddd4j {@link Cache} SPI。
 * 值通过 Jackson 序列化为 JSON 字符串存储到 Redis。
 *
 * <p>Jedis 5+ 废弃了 {@code JedisPool} + {@code Jedis} 的 try-with-resources 模式，
 * 改为 {@link UnifiedJedis} 统一接口（内部自带连接池管理），本实现跟随此变化。
 *
 * <p>使用示例：
 * <pre>{@code
 *   // Jedis 7.x
 *   RedisClient client = RedisClient.create("localhost", 6379);
 *   CacheConfig config = CacheConfig.builder("user").expireAfterWriteSeconds(300).build();
 *   Cache<String, User> cache = new JedisCache<>(client, config, User.class);
 *   cache.put("123", user);
 *   User cached = cache.getIfPresent("123");
 * }</pre>
 *
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class JedisCache<V> implements CasCache<String, V>, AtomicCache<String, V> {

    /**
     * Lua 脚本：CAS 替换（仅当值匹配时才更新）
     */
    private static final String CAS_REPLACE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('set', KEYS[1], ARGV[2]) else return 0 end";
    /**
     * Lua 脚本：CAS 删除（仅当值匹配时才删除）
     */
    private static final String CAS_DELETE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    /**
     * Lua 库存扣减脚本
     */
    private static final String STOCK_DECR_SCRIPT =
            "if (redis.call('EXISTS', KEYS[1]) == 1) then " +
                    "  local stock = tonumber(redis.call('GET', KEYS[1])); " +
                    "  local num = tonumber(ARGV[1]); " +
                    "  if (num <= 0) then return -4 end; " +
                    "  if (stock <= 0) then return -1 end; " +
                    "  if (stock >= num) then return redis.call('INCRBY', KEYS[1], 0 - num) end; " +
                    "  return -2; " +
                    "end; " +
                    "return -3;";
    /**
     * Lua 库存回补脚本
     */
    private static final String STOCK_INCR_SCRIPT =
            "if (redis.call('EXISTS', KEYS[1]) == 1) then " +
                    "  local num = tonumber(ARGV[1]); " +
                    "  if (num < 0) then return -4 end; " +
                    "  return redis.call('INCRBY', KEYS[1], num); " +
                    "end; " +
                    "return -3;";
    private final UnifiedJedis jedis;
    private final SetParams setParams;
    private final long expireSeconds;
    private final Class<V> valueType;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    /**
     * 构造 Jedis 缓存。
     *
     * @param jedis        Jedis 统一接口（RedisClient / JedisPooled 等）
     * @param config       缓存配置
     * @param valueType    值类型
     * @param objectMapper Jackson ObjectMapper
     */
    public JedisCache(UnifiedJedis jedis, CacheConfig config, Class<V> valueType, ObjectMapper objectMapper) {
        this.jedis = Objects.requireNonNull(jedis);
        this.valueType = Objects.requireNonNull(valueType);
        this.objectMapper = Objects.nonNull(objectMapper) ? objectMapper : new ObjectMapper();
        this.keyPrefix = config.getName() + ":";
        long expSec = config.getExpireAfterWriteSeconds();
        this.expireSeconds = expSec;
        this.setParams = expSec > 0 ? SetParams.setParams().ex(expSec) : null;
    }

    /**
     * 构造 Jedis 缓存（默认 ObjectMapper）。
     */
    public JedisCache(UnifiedJedis jedis, CacheConfig config, Class<V> valueType) {
        this(jedis, config, valueType, new ObjectMapper());
    }

    private String key(String key) {
        return keyPrefix + key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getIfPresent(String key) {
        try {
            String json = jedis.get(key(key));
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
            if (Objects.nonNull(setParams)) {
                jedis.set(key(key), json, setParams);
            } else {
                jedis.set(key(key), json);
            }
        } catch (Exception e) {
            // 写入失败忽略
        }
    }

    @Override
    public void invalidate(String key) {
        try {
            jedis.del(key(key));
        } catch (Exception e) {
            // 删除失败忽略
        }
    }

    // ==================== CasCache 实现（基于 Redis SETNX + Lua） ====================

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

    /**
     * 获取底层 Jedis 统一接口实例。
     *
     * @return UnifiedJedis 实例
     */
    public UnifiedJedis unwrap() {
        return jedis;
    }

    @Override
    public boolean putIfAbsent(String key, V value) {
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            SetParams nxParams = expireSeconds > 0
                    ? SetParams.setParams().nx().ex(expireSeconds)
                    : SetParams.setParams().nx();
            String result = jedis.set(key(key), json, nxParams);
            return "OK".equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== AtomicCache 实现（基于 Jedis incr/decr + Lua 库存脚本） ====================

    @Override
    public boolean replace(String key, V expected, V newValue) {
        try {
            if (Objects.isNull(expected)) {
                return putIfAbsent(key, newValue);
            }
            String expectedJson = (expected instanceof String) ? (String) expected : objectMapper.writeValueAsString(expected);
            String newJson = (newValue instanceof String) ? (String) newValue : objectMapper.writeValueAsString(newValue);
            Object result = jedis.eval(CAS_REPLACE_SCRIPT, 1, key(key), expectedJson, newJson);
            return Objects.nonNull(result) && !Long.valueOf(0).equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean removeIf(String key, V expected) {
        try {
            String expectedJson = (expected instanceof String) ? (String) expected : objectMapper.writeValueAsString(expected);
            Object result = jedis.eval(CAS_DELETE_SCRIPT, 1, key(key), expectedJson);
            return Objects.nonNull(result) && !Long.valueOf(0).equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long increment(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("增量必须 >= 0");
        }
        return jedis.incrBy(key(key), delta);
    }

    @Override
    public long increment(String key, long delta, long seconds) {
        long result = jedis.incrBy(key(key), delta);
        if (seconds > 0) {
            jedis.expire(key(key), seconds);
        }
        return result;
    }

    @Override
    public long decrement(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("减量必须 >= 0");
        }
        return jedis.incrBy(key(key), -delta);
    }

    @Override
    public double incrementFloat(String key, double delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("增量必须 >= 0");
        }
        return jedis.incrByFloat(key(key), delta);
    }

    @Override
    public double decrementFloat(String key, double delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("减量必须 >= 0");
        }
        return jedis.incrByFloat(key(key), -delta);
    }

    @Override
    public long stockDecrement(String key, long quantity) {
        Object result = jedis.eval(STOCK_DECR_SCRIPT, 1, key(key), String.valueOf(quantity));
        return result instanceof Long ? (Long) result : Long.parseLong(String.valueOf(result));
    }

    @Override
    public long stockIncrement(String key, long quantity) {
        Object result = jedis.eval(STOCK_INCR_SCRIPT, 1, key(key), String.valueOf(quantity));
        return result instanceof Long ? (Long) result : Long.parseLong(String.valueOf(result));
    }

    // ==================== TTL 管理 ====================

    @Override
    public boolean expire(String key, long seconds) {
        return jedis.expire(key(key), seconds) == 1L;
    }

    @Override
    public long getExpire(String key) {
        return jedis.ttl(key(key));
    }

    @Override
    public boolean persist(String key) {
        return jedis.persist(key(key)) == 1L;
    }

}
