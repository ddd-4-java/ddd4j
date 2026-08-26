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
package io.ddd4j.cache.redisson;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cache.*;
import io.ddd4j.kit.lang.StrKit;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Redisson 原生缓存实现。
 *
 * <p>传入 {@link RedissonClient} 实例，直接实现 ddd4j {@link Cache} SPI。
 * 值通过 Jackson 序列化为 JSON 字符串存储到 Redis。
 *
 * <p>相比 Jedis/Lettuce，Redisson 额外提供分布式锁能力：
 * 通过 {@link #tryLock(String, long)} 获取分布式锁。
 *
 * <p>使用示例：
 * <pre>{@code
 *   RedissonClient client = Redisson.create();
 *   CacheConfig config = CacheConfig.builder("user").expireAfterWriteSeconds(300).build();
 *   Cache<String, User> cache = new RedissonCache<>(client, config, User.class);
 *   cache.put("123", user);
 *   User cached = cache.getIfPresent("123");
 *
 *   // 分布式锁
 *   Lock lock = ((RedissonCache<?>) cache).tryLock("resource:1", 30);
 *   try {
 *       lock.lock();
 *       // 业务逻辑
 *   } finally {
 *       lock.unlock();
 *   }
 * }</pre>
 *
 * @param <V> 缓存值类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class RedissonCache<V> implements CasCache<String, V>, CacheLock, AtomicCache<String, V> {

    /**
     * Lua 库存扣减脚本（-1=售罄, -2=不足, -3=未初始化, -4=非法参数, >=0=剩余）
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
    private final RedissonClient redissonClient;
    private final long expireSeconds;
    private final Class<V> valueType;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    /**
     * 构造 Redisson 缓存。
     *
     * @param redissonClient Redisson 客户端
     * @param config         缓存配置
     * @param valueType      值类型
     * @param objectMapper   Jackson ObjectMapper
     */
    public RedissonCache(RedissonClient redissonClient, CacheConfig config,
                         Class<V> valueType, ObjectMapper objectMapper) {
        this.redissonClient = Objects.requireNonNull(redissonClient);
        this.expireSeconds = config.getExpireAfterWriteSeconds() > 0 ? config.getExpireAfterWriteSeconds() : 3600;
        this.valueType = Objects.requireNonNull(valueType);
        this.keyPrefix = config.getName() + ":";
        this.objectMapper = Objects.nonNull(objectMapper) ? objectMapper : JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
    }

    public RedissonCache(RedissonClient redissonClient, CacheConfig config, Class<V> valueType, Function<String, ObjectMapper> objectMapperFactory) {
        this(redissonClient, config, valueType, objectMapperFactory.apply(valueType.getName()));
    }

    /**
     * 构造 Redisson 缓存（默认 ObjectMapper）。
     */
    public RedissonCache(RedissonClient redissonClient, CacheConfig config, Class<V> valueType) {
        this(redissonClient, config, valueType, JsonMapper.builder().build());
    }

    private String key(String key) {
        return keyPrefix + key;
    }

    @Override
    public V getIfPresent(String key) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key(key));
            String json = bucket.get();
            if (Objects.isNull(json)) {
                return null;
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
            RBucket<String> bucket = redissonClient.getBucket(key(key));
            if (expireSeconds > 0) {
                bucket.set(json, Duration.ofSeconds(expireSeconds));
            } else {
                bucket.set(json);
            }
        } catch (Exception e) {
            // 写入失败忽略
        }
    }

    @Override
    public void invalidate(String key) {
        try {
            redissonClient.getBucket(key(key)).delete();
        } catch (Exception e) {
            // 删除失败忽略
        }
    }

    @Override
    public void invalidateAll() {
        // Redis 不建议 flushAll，需通过 key 前缀逐个删除
    }

    // ==================== CacheLock 实现（基于 Redisson 分布式锁） ====================

    @Override
    public long estimatedSize() {
        return -1;
    }

    @Override
    public CacheStats stats() {
        return null;
    }

    @Override
    public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
        try {
            return redissonClient.getLock(keyPrefix + "lock:" + key)
                    .tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ==================== CasCache 实现（基于 Redisson RBucket CAS） ====================

    @Override
    public void unlock(String key) {
        try {
            redissonClient.getLock(keyPrefix + "lock:" + key).unlock();
        } catch (Exception e) {
            // 锁已过期或未持有
        }
    }

    /**
     * 获取底层 Redisson RLock 实例（用于高级锁操作）。
     *
     * @param key 锁键
     * @return RLock 实例
     */
    public RLock getLock(String key) {
        return redissonClient.getLock(keyPrefix + "lock:" + key);
    }

    @Override
    public boolean putIfAbsent(String key, V value) {
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            RBucket<String> bucket = redissonClient.getBucket(key(key));
            if (expireSeconds > 0) {
                return bucket.setIfAbsent(json, Duration.ofSeconds(expireSeconds));
            }
            return bucket.setIfAbsent(json);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== AtomicCache 实现（基于 Redisson RAtomicLong + Lua 库存脚本） ====================

    @Override
    public boolean replace(String key, V expected, V newValue) {
        try {
            String expectedJson = (expected instanceof String) ? (String) expected : objectMapper.writeValueAsString(expected);
            String newJson = (newValue instanceof String) ? (String) newValue : objectMapper.writeValueAsString(newValue);
            RBucket<String> bucket = redissonClient.getBucket(key(key));
            if (Objects.isNull(expected)) {
                return bucket.setIfAbsent(newJson);
            }
            return bucket.compareAndSet(expectedJson, newJson);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean removeIf(String key, V expected) {
        try {
            String expectedJson = (expected instanceof String) ? (String) expected : objectMapper.writeValueAsString(expected);
            RBucket<String> bucket = redissonClient.getBucket(key(key));
            return bucket.compareAndSet(expectedJson, null);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long increment(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("增量必须 >= 0");
        }
        return redissonClient.getAtomicLong(key(key)).addAndGet(delta);
    }

    @Override
    public long increment(String key, long delta, long seconds) {
        long result = increment(key, delta);
        // 设置过期（RAtomicLong 的 expire）
        redissonClient.getAtomicLong(key(key)).expire(Duration.ofSeconds(seconds));
        return result;
    }

    @Override
    public long decrement(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("减量必须 >= 0");
        }
        return redissonClient.getAtomicLong(key(key)).addAndGet(-delta);
    }

    @Override
    public double incrementFloat(String key, double delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("增量必须 >= 0");
        }
        // Redisson 无原子浮点操作，使用 RBucket + Lua INCRBYFLOAT
        org.redisson.api.RScript script = redissonClient.getScript();
        return script.eval(org.redisson.api.RScript.Mode.READ_WRITE,
                "if (redis.call('EXISTS', KEYS[1]) == 1) then return redis.call('INCRBYFLOAT', KEYS[1], ARGV[1]) else redis.call('SET', KEYS[1], ARGV[1]) return tonumber(ARGV[1]) end",
                org.redisson.api.RScript.ReturnType.STATUS,
                java.util.Collections.singletonList(key(key)),
                String.valueOf(delta));
    }

    @Override
    public double decrementFloat(String key, double delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("减量必须 >= 0");
        }
        return incrementFloat(key, -delta);
    }

    @Override
    public long stockDecrement(String key, long quantity) {
        org.redisson.api.RScript script = redissonClient.getScript();
        return script.eval(org.redisson.api.RScript.Mode.READ_WRITE,
                STOCK_DECR_SCRIPT, org.redisson.api.RScript.ReturnType.INTEGER,
                java.util.Collections.singletonList(key(key)), quantity);
    }

    @Override
    public long stockIncrement(String key, long quantity) {
        org.redisson.api.RScript script = redissonClient.getScript();
        return script.eval(org.redisson.api.RScript.Mode.READ_WRITE,
                STOCK_INCR_SCRIPT, org.redisson.api.RScript.ReturnType.INTEGER,
                java.util.Collections.singletonList(key(key)), quantity);
    }

    // ==================== TTL 管理 ====================

    @Override
    public boolean expire(String key, long seconds) {
        return redissonClient.getBucket(key(key)).expire(Duration.ofSeconds(seconds));
    }

    @Override
    public long getExpire(String key) {
        long ttl = redissonClient.getBucket(key(key)).remainTimeToLive();
        return ttl < 0 ? ttl : ttl / 1000;
    }

    @Override
    public boolean persist(String key) {
        return redissonClient.getBucket(key(key)).clearExpire();
    }

    /**
     * 获取底层 Redisson 客户端。
     *
     * @return RedissonClient 实例
     */
    public RedissonClient unwrap() {
        return redissonClient;
    }

    // ==================== CAS SPI 实现（基于 RBucket.compareAndSet 值比较 + 重试）====================

    @Override
    @SuppressWarnings("unchecked")
    public V compareAndSet(String key, long expireSeconds, CASOperation<V, V> operation) {
        String cachedKey = key(key);
        int maxTries = operation.maxRetries() > 0 ? operation.maxRetries() : 16;
        for (int attempt = 0; attempt < maxTries; attempt++) {
            RBucket<String> bucket = redissonClient.getBucket(cachedKey);
            String currentJson = bucket.get();
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
            if (Objects.isNull(currentJson)) {
                // key 不存在：setIfAbsent
                if (bucket.setIfAbsent(newJson)) {
                    if (expireSeconds > 0) {
                        bucket.expire(Duration.ofSeconds(expireSeconds));
                    }
                    return newValue;
                }
                continue;
            }
            // Redisson RBucket.compareAndSet(old, new) — 值比较原子操作
            if (bucket.compareAndSet(currentJson, newJson)) {
                if (expireSeconds > 0) {
                    bucket.expire(Duration.ofSeconds(expireSeconds));
                }
                return newValue;
            }
        }
        return null;
    }

    @Override
    public boolean compareAndSet(String key, V expectedOldValue, V newValue) {
        String cachedKey = key(key);
        RBucket<String> bucket = redissonClient.getBucket(cachedKey);
        String expectedJson = Objects.isNull(expectedOldValue) ? null : serialize(expectedOldValue);
        String newJson = serialize(newValue);
        if (Objects.isNull(expectedJson)) {
            return bucket.setIfAbsent(newJson);
        }
        return bucket.compareAndSet(expectedJson, newJson);
    }

    private String serialize(V value) {
        if (Objects.isNull(value)) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
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
