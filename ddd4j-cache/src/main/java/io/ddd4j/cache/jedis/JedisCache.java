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
package io.ddd4j.cache.jedis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cache.*;
import io.ddd4j.kit.lang.StrKit;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

import java.util.Arrays;
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
@SuppressWarnings("unchecked")
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
    /**
     * Redis Lua CAS 脚本：if get==old then set new end。
     * <p>KEYS[1]=key, ARGV[1]=expectedOld, ARGV[2]=newValue, ARGV[3]=expire
     */
    private static final String LUA_CAS =
            "local cur = redis.call('GET', KEYS[1]) " +
                    "if cur == ARGV[1] then " +
                    "  redis.call('SET', KEYS[1], ARGV[2]) " +
                    "  if tonumber(ARGV[3]) > 0 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end " +
                    "  return 1 " +
                    "end " +
                    "return 0";
    /** \u6267\u884c\u7f13\u5b58\u8bfb\u5199\u548c\u4e8b\u52a1\u547d\u4ee4\u7684 Jedis \u5ba2\u6237\u7aef\u3002 */
    private final UnifiedJedis jedis;
    /** \u5199\u5165\u7f13\u5b58\u65f6\u4f7f\u7528\u7684 Redis SET \u53c2\u6570\u3002 */
    private final SetParams setParams;
    /** \u9ed8\u8ba4\u7f13\u5b58\u5b58\u6d3b\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u79d2\u3002 */
    private final long expireSeconds;
    /** \u53cd\u5e8f\u5217\u5316\u7f13\u5b58\u6761\u76ee\u65f6\u4f7f\u7528\u7684\u503c\u7c7b\u578b\u3002 */
    private final Class<V> valueType;
    /** \u8d1f\u8d23\u5728 Redis \u6216 Memcached \u4e2d\u5b58\u50a8 JSON \u503c\u7684\u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper;
    /** \u7528\u4e8e\u9694\u79bb\u5f53\u524d\u7f13\u5b58\u547d\u540d\u7a7a\u95f4\u7684\u952e\u524d\u7f00\u3002 */
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
        this.objectMapper = Objects.nonNull(objectMapper) ? objectMapper : JsonMapper.builder().build();
        this.keyPrefix = config.getName() + ":";
        long expSec = config.getExpireAfterWriteSeconds();
        this.expireSeconds = expSec;
        this.setParams = expSec > 0 ? SetParams.setParams().ex(expSec) : null;
    }

    /**
     * 构造 Jedis 缓存（默认 ObjectMapper）。
     *
     * @param jedis     Jedis 客户端
     * @param config    缓存配置
     * @param valueType 缓存值类型
     */
    public JedisCache(UnifiedJedis jedis, CacheConfig config, Class<V> valueType) {
        this(jedis, config, valueType, JsonMapper.builder().build());
    }

    /**
     * \u4e3a\u4e1a\u52a1\u952e\u6dfb\u52a0\u5f53\u524d\u7f13\u5b58\u547d\u540d\u7a7a\u95f4\u524d\u7f00\u3002
     * @param key \u7f13\u5b58\u952e
     * @return \u5199\u5165\u540e\u7aef\u4f7f\u7528\u7684\u7f13\u5b58\u952e
     */
    private String key(String key) {
        return keyPrefix + key;
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    // ==================== CasCache 实现（基于 Redis SETNX + Lua） ====================

    /** {@inheritDoc} */
    @Override
    public void invalidate(String key) {
        try {
            jedis.del(key(key));
        } catch (Exception e) {
            // 删除失败忽略
        }
    }

    /** \u4fdd\u7559\u540e\u7aef\u73b0\u6709\u952e\u3002\u8c03\u7528\u65b9\u9700\u901a\u8fc7\u660e\u786e\u7684\u952e\u9010\u4e2a\u5931\u6548\u3002 */
    @Override
    public void invalidateAll() {
        // Redis 不建议 flushAll，需通过 key 前缀逐个删除
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
     * 获取底层 Jedis 统一接口实例。
     *
     * @return UnifiedJedis 实例
     */
    public UnifiedJedis unwrap() {
        return jedis;
    }

    // ==================== AtomicCache 实现（基于 Jedis incr/decr + Lua 库存脚本） ====================

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public long increment(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("增量必须 >= 0");
        }
        return jedis.incrBy(key(key), delta);
    }

    /** {@inheritDoc} */
    @Override
    public long increment(String key, long delta, long seconds) {
        long result = jedis.incrBy(key(key), delta);
        if (seconds > 0) {
            jedis.expire(key(key), seconds);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public long decrement(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("减量必须 >= 0");
        }
        return jedis.incrBy(key(key), -delta);
    }

    /** {@inheritDoc} */
    @Override
    public double incrementFloat(String key, double delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("增量必须 >= 0");
        }
        return jedis.incrByFloat(key(key), delta);
    }

    /** {@inheritDoc} */
    @Override
    public double decrementFloat(String key, double delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("减量必须 >= 0");
        }
        return jedis.incrByFloat(key(key), -delta);
    }

    /** {@inheritDoc} */
    @Override
    public long stockDecrement(String key, long quantity) {
        Object result = jedis.eval(STOCK_DECR_SCRIPT, 1, key(key), String.valueOf(quantity));
        return result instanceof Long ? (Long) result : Long.parseLong(String.valueOf(result));
    }

    // ==================== TTL 管理 ====================

    /** {@inheritDoc} */
    @Override
    public long stockIncrement(String key, long quantity) {
        Object result = jedis.eval(STOCK_INCR_SCRIPT, 1, key(key), String.valueOf(quantity));
        return result instanceof Long ? (Long) result : Long.parseLong(String.valueOf(result));
    }

    /** {@inheritDoc} */
    @Override
    public boolean expire(String key, long seconds) {
        return jedis.expire(key(key), seconds) == 1L;
    }

    /** {@inheritDoc} */
    @Override
    public long getExpire(String key) {
        return jedis.ttl(key(key));
    }

    // ==================== CAS SPI 实现（基于 Redis Lua 脚本原子操作）====================

    /** {@inheritDoc} */
    @Override
    public boolean persist(String key) {
        return jedis.persist(key(key)) == 1L;
    }

    /** {@inheritDoc} */
    @Override
    public V compareAndSet(String key, long expireSeconds, CASOperation<V, V> operation) {
        String cachedKey = key(key);
        int maxTries = operation.maxRetries() > 0 ? operation.maxRetries() : 16;
        for (int attempt = 0; attempt < maxTries; attempt++) {
            String currentJson = jedis.get(cachedKey);
            V currentValue = deserialize(currentJson);
            io.ddd4j.core.cache.GetsResponse<V> resp = new io.ddd4j.core.cache.GetsResponse<V>() {
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
            // 用 Lua 原子比较
            Object result = jedis.eval(LUA_CAS, Arrays.asList(cachedKey),
                    Arrays.asList(Objects.isNull(currentJson) ? "" : currentJson, newJson, String.valueOf(expireSeconds)));
            if (Long.valueOf(1).equals(result)) {
                return newValue;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean compareAndSet(String key, V expectedOldValue, V newValue) {
        String cachedKey = key(key);
        String expectedJson = Objects.isNull(expectedOldValue) ? null : serialize(expectedOldValue);
        String newJson = serialize(newValue);
        Object result = jedis.eval(LUA_CAS, Arrays.asList(cachedKey),
                Arrays.asList(Objects.isNull(expectedJson) ? "" : expectedJson, newJson, "0"));
        return Long.valueOf(1).equals(result);
    }

    /**
     * \u4fdd\u7559\u5b57\u7b26\u4e32\u503c\u539f\u6837\uff0c\u5c06\u5176\u4ed6\u975e\u7a7a\u503c\u7f16\u7801\u4e3a JSON\u3002
     * @param value \u5f85\u5199\u5165\u7684\u7f13\u5b58\u503c
     * @return \u5df2\u7f16\u7801\u7684\u5b57\u7b26\u4e32\uff0c\u7a7a\u503c\u6216\u7f16\u7801\u5931\u8d25\u65f6\u8fd4\u56de\u7a7a\u5b57\u7b26\u4e32
     */
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

    /**
     * \u6309\u7167\u9002\u914d\u5668\u7684\u503c\u7c7b\u578b\u53cd\u5e8f\u5217\u5316\u7f13\u5b58\u6587\u672c\u3002
     * @param json \u4ece\u7f13\u5b58\u540e\u7aef\u8bfb\u51fa\u7684\u5b57\u7b26\u4e32
     * @return \u89e3\u7801\u540e\u7684\u7f13\u5b58\u503c\uff0c\u8f93\u5165\u4e3a\u7a7a\u6216\u89e3\u7801\u5931\u8d25\u65f6\u8fd4\u56de\u7a7a
     */
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
