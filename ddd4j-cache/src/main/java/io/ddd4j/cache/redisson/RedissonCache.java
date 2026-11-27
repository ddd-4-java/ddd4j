package io.ddd4j.cache.redisson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    /** \u7528\u4e8e\u8bbf\u95ee Redis \u7f13\u5b58\u548c\u9501\u7684 Redisson \u5ba2\u6237\u7aef\u3002 */
    private final RedissonClient redissonClient;
    /** \u9ed8\u8ba4\u7f13\u5b58\u5b58\u6d3b\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u79d2\u3002 */
    private final long expireSeconds;
    /** \u53cd\u5e8f\u5217\u5316\u7f13\u5b58\u6761\u76ee\u65f6\u4f7f\u7528\u7684\u503c\u7c7b\u578b\u3002 */
    private final Class<V> valueType;
    /** \u8d1f\u8d23\u5728 Redis \u6216 Memcached \u4e2d\u5b58\u50a8 JSON \u503c\u7684\u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper;
    /** \u7528\u4e8e\u9694\u79bb\u5f53\u524d\u7f13\u5b58\u547d\u540d\u7a7a\u95f4\u7684\u952e\u524d\u7f00\u3002 */
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
        this.objectMapper = Objects.nonNull(objectMapper) ? objectMapper : new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a ObjectMapper \u5de5\u5382\u521b\u5efa Redisson \u7f13\u5b58\u3002
     * @param redissonClient Redisson \u5ba2\u6237\u7aef
     * @param config \u7f13\u5b58\u914d\u7f6e
     * @param valueType \u7f13\u5b58\u503c\u7c7b\u578b
     * @param objectMapperFactory ObjectMapper \u5de5\u5382
     */
    public RedissonCache(RedissonClient redissonClient, CacheConfig config, Class<V> valueType, Function<String, ObjectMapper> objectMapperFactory) {
        this(redissonClient, config, valueType, objectMapperFactory.apply(valueType.getName()));
    }

    /**
     * \u6784\u9020 Redisson \u7f13\u5b58\uff08\u9ed8\u8ba4 ObjectMapper\uff09\u3002
     *
     * @param redissonClient Redisson \u5ba2\u6237\u7aef
     * @param config \u7f13\u5b58\u914d\u7f6e
     * @param valueType \u7f13\u5b58\u503c\u7c7b\u578b
     */
    public RedissonCache(RedissonClient redissonClient, CacheConfig config, Class<V> valueType) {
        this(redissonClient, config, valueType, new ObjectMapper());
    }

    /**
     * \u4e3a\u4e1a\u52a1\u952e\u6dfb\u52a0\u5f53\u524d\u7f13\u5b58\u547d\u540d\u7a7a\u95f4\u524d\u7f00\u3002
     * @param key \u7f13\u5b58\u952e
     * @return \u5199\u5165\u540e\u7aef\u4f7f\u7528\u7684\u7f13\u5b58\u952e
     */
    private String key(String key) {
        return keyPrefix + key;
    }

    /**
     * 读取现有缓存值。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getBucket(key(key))</code>。</p>
     *
     * @param key 当前操作使用的键
     * @return 根据执行路径返回<code>null</code>、<code>objectMapper.readValue(json, valueType)</code>
     */
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

    /**
     * 读取调用指定的当前值。
     *
     * <p>Redisson 实现调用 <code>getIfPresent(key)</code>。</p>
     *
     * @param key 当前操作使用的键
     * @param mappingFunction 传给 <code>mappingFunction.apply(key)</code>
     * @return 实际返回<code>value</code>
     */
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

    /**
     * 将给定数据写入当前存储。
     *
     * <p>Redisson 实现调用 <code>objectMapper.writeValueAsString(value)</code>。</p>
     *
     * @param key 当前操作使用的键
     * @param value 要写入或作为比较条件传入的值
     */
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

    /**
     * 使指定键的缓存条目失效。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getBucket(key(key)).delete()</code>。</p>
     *
     * @param key 当前操作使用的键
     */
    @Override
    public void invalidate(String key) {
        try {
            redissonClient.getBucket(key(key)).delete();
        } catch (Exception e) {
            // 删除失败忽略
        }
    }

    /** \u4fdd\u7559\u540e\u7aef\u73b0\u6709\u952e\u3002\u8c03\u7528\u65b9\u9700\u901a\u8fc7\u660e\u786e\u7684\u952e\u9010\u4e2a\u5931\u6548\u3002 */
    @Override
    public void invalidateAll() {
        // Redis 不建议 flushAll，需通过 key 前缀逐个删除
    }

    // ==================== CacheLock 实现（基于 Redisson 分布式锁） ====================

    /**
     * \u62a5\u544a\u5f53\u524d\u540e\u7aef\u7684\u7f13\u5b58\u6761\u76ee\u6570\u91cf\u662f\u5426\u53ef\u7528\u3002
     * @return \u56fa\u5b9a\u8fd4\u56de -1\uff0c\u8868\u793a\u6b64\u9002\u914d\u5668\u4e0d\u4f30\u7b97\u6761\u76ee\u6570
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
     * 尝试获取分布式锁。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getLock(keyPrefix + "lock:" + key).tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS)</code>。</p>
     *
     * @param key 锁键
     * @param waitSeconds 最多等待时间（秒），0 表示不等待
     * @param leaseSeconds 持有时间（秒），超过自动释放（防死锁）
     * @return 根据执行路径返回<code>redissonClient.getLock(keyPrefix + "lock:" + key).tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS)</code>、<code>false</code>
     */
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

    /**
     * 释放锁。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getLock(keyPrefix + "lock:" + key).unlock()</code>。</p>
     *
     * @param key 锁键
     */
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

    /**
     * 在键尚无缓存值时写入给定值。
     *
     * <p>Redisson 实现调用 <code>objectMapper.writeValueAsString(value)</code>。</p>
     *
     * @param key 缓存键
     * @param value 缓存值
     * @return 根据执行路径返回<code>bucket.setIfAbsent(json, Duration.ofSeconds(expireSeconds))</code>、<code>bucket.setIfAbsent(json)</code>、<code>false</code>
     */
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

    /**
     * 使用给定的新值更新数据。
     *
     * <p>Redisson 实现调用 <code>objectMapper.writeValueAsString(expected)</code>。</p>
     *
     * @param key 缓存键
     * @param expected 期望的旧值（null 表示期望 key 不存在）
     * @param newValue 新值
     * @return 根据执行路径返回<code>bucket.setIfAbsent(newJson)</code>、<code>bucket.compareAndSet(expectedJson, newJson)</code>、<code>false</code>
     */
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

    /**
     * 在缓存值符合预期时删除该键。
     *
     * <p>Redisson 实现调用 <code>objectMapper.writeValueAsString(expected)</code>。</p>
     *
     * @param key 缓存键
     * @param expected 期望的旧值
     * @return 根据执行路径返回<code>bucket.compareAndSet(expectedJson, null)</code>、<code>false</code>
     */
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

    /**
     * 对当前计数或数值执行增加操作。
     *
     * <p>满足 <code>delta &lt; 0</code> 时抛出 <code>IllegalArgumentException</code>。 Redisson 实现调用 <code>redissonClient.getAtomicLong(key(key)).addAndGet(delta)</code>。</p>
     *
     * @param key 缓存键
     * @param delta 增量（必须 >= 0）
     * @return 实际返回<code>redissonClient.getAtomicLong(key(key)).addAndGet(delta)</code>
     */
    @Override
    public long increment(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("增量必须 >= 0");
        }
        return redissonClient.getAtomicLong(key(key)).addAndGet(delta);
    }

    /**
     * 对当前计数或数值执行增加操作。
     *
     * <p>Redisson 实现调用 <code>increment(key, delta)</code>。</p>
     *
     * @param key 缓存键
     * @param delta 增量（必须 >= 0）
     * @param seconds 过期时间（秒），仅对首次创建有效
     * @return 实际返回<code>result</code>
     */
    @Override
    public long increment(String key, long delta, long seconds) {
        long result = increment(key, delta);
        // 设置过期（RAtomicLong 的 expire）
        redissonClient.getAtomicLong(key(key)).expire(Duration.ofSeconds(seconds));
        return result;
    }

    /**
     * 对当前计数或数值执行减少操作。
     *
     * <p>满足 <code>delta &lt; 0</code> 时抛出 <code>IllegalArgumentException</code>。 Redisson 实现调用 <code>redissonClient.getAtomicLong(key(key)).addAndGet(-delta)</code>。</p>
     *
     * @param key 缓存键
     * @param delta 减量（必须 >= 0）
     * @return 实际返回<code>redissonClient.getAtomicLong(key(key)).addAndGet(-delta)</code>
     */
    @Override
    public long decrement(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("减量必须 >= 0");
        }
        return redissonClient.getAtomicLong(key(key)).addAndGet(-delta);
    }

    /**
     * 对键对应的数值执行浮点加法。
     *
     * <p>满足 <code>delta &lt; 0</code> 时抛出 <code>IllegalArgumentException</code>。 Redisson 实现调用 <code>redissonClient.getScript()</code>。</p>
     *
     * @param key 缓存键
     * @param delta 增量（必须 >= 0）
     * @return 递增后的值
     */
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

    /**
     * 对键对应的数值执行浮点减法。
     *
     * <p>满足 <code>delta &lt; 0</code> 时抛出 <code>IllegalArgumentException</code>。 Redisson 实现调用 <code>incrementFloat(key, -delta)</code>。</p>
     *
     * @param key 缓存键
     * @param delta 减量（必须 >= 0）
     * @return 实际返回<code>incrementFloat(key, -delta)</code>
     */
    @Override
    public double decrementFloat(String key, double delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("减量必须 >= 0");
        }
        return incrementFloat(key, -delta);
    }

    /**
     * 尝试扣减键对应的库存。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getScript()</code>。</p>
     *
     * @param key 缓存键
     * @param quantity 扣减数量（必须 > 0）
     * @return {@code >= 0} 剩余库存；负数表示失败（见返回值约定）
     */
    @Override
    public long stockDecrement(String key, long quantity) {
        org.redisson.api.RScript script = redissonClient.getScript();
        return script.eval(org.redisson.api.RScript.Mode.READ_WRITE,
                STOCK_DECR_SCRIPT, org.redisson.api.RScript.ReturnType.INTEGER,
                java.util.Collections.singletonList(key(key)), quantity);
    }

    /**
     * 增加键对应的库存。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getScript()</code>。</p>
     *
     * @param key 缓存键
     * @param quantity 回补数量（必须 > 0）
     * @return {@code >= 0} 回补后的库存；负数表示失败
     */
    @Override
    public long stockIncrement(String key, long quantity) {
        org.redisson.api.RScript script = redissonClient.getScript();
        return script.eval(org.redisson.api.RScript.Mode.READ_WRITE,
                STOCK_INCR_SCRIPT, org.redisson.api.RScript.ReturnType.INTEGER,
                java.util.Collections.singletonList(key(key)), quantity);
    }

    // ==================== TTL 管理 ====================

    /**
     * 为指定键更新失效时间。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getBucket(key(key)).expire(Duration.ofSeconds(seconds))</code>。</p>
     *
     * @param key 当前操作使用的键
     * @param seconds 传给 <code>redissonClient.getBucket(key(key)).expire(Duration.ofSeconds(seconds))</code>
     * @return 实际返回<code>redissonClient.getBucket(key(key)).expire(Duration.ofSeconds(seconds))</code>
     */
    @Override
    public boolean expire(String key, long seconds) {
        return redissonClient.getBucket(key(key)).expire(Duration.ofSeconds(seconds));
    }

    /**
     * 查询键的剩余有效时间。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getBucket(key(key)).remainTimeToLive()</code>。</p>
     *
     * @param key 当前操作使用的键
     * @return 实际返回<code>ttl &lt; 0 ? ttl : ttl / 1000</code>
     */
    @Override
    public long getExpire(String key) {
        long ttl = redissonClient.getBucket(key(key)).remainTimeToLive();
        return ttl < 0 ? ttl : ttl / 1000;
    }

    /**
     * 移除指定键上的到期限制。
     *
     * <p>Redisson 实现调用 <code>redissonClient.getBucket(key(key)).clearExpire()</code>。</p>
     *
     * @param key 当前操作使用的键
     * @return 实际返回<code>redissonClient.getBucket(key(key)).clearExpire()</code>
     */
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

    /**
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>Redisson 实现调用 <code>key(key)</code>。</p>
     *
     * @param key 当前操作使用的键
     * @param expireSeconds 传给 <code>bucket.expire(Duration.ofSeconds(expireSeconds))</code>
     * @param operation 传给 <code>operation.maxRetries()</code>
     * @return 根据执行路径返回<code>null</code>、<code>newValue</code>
     */
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

    /**
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>Redisson 实现调用 <code>key(key)</code>。</p>
     *
     * @param key 当前操作使用的键
     * @param expectedOldValue 传给 <code>serialize(expectedOldValue)</code>
     * @param newValue 传给 <code>serialize(newValue)</code>
     * @return 根据执行路径返回<code>bucket.setIfAbsent(newJson)</code>、<code>bucket.compareAndSet(expectedJson, newJson)</code>
     */
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

    /**
     * \u4fdd\u7559\u5b57\u7b26\u4e32\u503c\u539f\u6837\uff0c\u5c06\u5176\u4ed6\u975e\u7a7a\u503c\u7f16\u7801\u4e3a JSON\u3002
     * @param value \u5f85\u5199\u5165\u7684\u7f13\u5b58\u503c
     * @return \u5df2\u7f16\u7801\u7684\u5b57\u7b26\u4e32\uff0c\u7a7a\u503c\u6216\u7f16\u7801\u5931\u8d25\u65f6\u8fd4\u56de\u7a7a\u5f15\u7528
     */
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
