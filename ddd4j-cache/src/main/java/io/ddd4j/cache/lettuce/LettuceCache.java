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

    /** \u5728 Redis \u670d\u52a1\u7aef\u6bd4\u8f83\u5f53\u524d\u7f13\u5b58\u503c\u5e76\u6309\u8fc7\u671f\u914d\u7f6e\u539f\u5b50\u66f4\u65b0\u7684\u811a\u672c\u3002 */
    private static final String LUA_CAS =
            "local cur = redis.call('GET', KEYS[1]) " +
                    "if cur == ARGV[1] then " +
                    "  redis.call('SET', KEYS[1], ARGV[2]) " +
                    "  if tonumber(ARGV[3]) > 0 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end " +
                    "  return 1 " +
                    "end " +
                    "return 0";
    /** \u6267\u884c Redis \u547d\u4ee4\u7684 Lettuce \u540c\u6b65\u63a5\u53e3\u3002 */
    private final RedisCommands<String, String> commands;
    /** \u7f13\u5b58\u5199\u5165\u65f6\u4f7f\u7528\u7684\u9ed8\u8ba4\u751f\u5b58\u65f6\u957f\u3002 */
    private final Duration expireDuration;
    /** \u53cd\u5e8f\u5217\u5316\u7f13\u5b58\u6761\u76ee\u65f6\u4f7f\u7528\u7684\u503c\u7c7b\u578b\u3002 */
    private final Class<V> valueType;
    /** \u8d1f\u8d23\u5728 Redis \u6216 Memcached \u4e2d\u5b58\u50a8 JSON \u503c\u7684\u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper;
    /** \u7528\u4e8e\u9694\u79bb\u5f53\u524d\u7f13\u5b58\u547d\u540d\u7a7a\u95f4\u7684\u952e\u524d\u7f00\u3002 */
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

    /**
     * \u4f7f\u7528\u6307\u5b9a ObjectMapper \u5de5\u5382\u521b\u5efa Lettuce \u7f13\u5b58\u3002
     * @param commands Redis \u547d\u4ee4\u63a5\u53e3
     * @param config \u7f13\u5b58\u914d\u7f6e
     * @param valueType \u7f13\u5b58\u503c\u7c7b\u578b
     * @param objectMapperFactory ObjectMapper \u5de5\u5382
     */
    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType, Function<String, ObjectMapper> objectMapperFactory) {
        this(commands, config, valueType, objectMapperFactory.apply(valueType.getName()));
    }

    /**
     * 构造 Lettuce 缓存（默认 ObjectMapper）。
     * @param commands 参数 commands
     * @param config 配置对象
     * @param valueType 值类型
     */
    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType) {
        this(commands, config, valueType, JsonMapper.builder().build());
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
     * <p>Lettuce 实现调用 <code>commands.get(key(key))</code>。</p>
     *
     * @param key 缓存键
     * @return 根据执行路径返回<code>null</code>、<code>(V)json</code>、<code>objectMapper.readValue(json, valueType)</code>
     */
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

    /**
     * 读取调用指定的当前值。
     *
     * <p>Lettuce 实现调用 <code>getIfPresent(key)</code>。</p>
     *
     * @param key 缓存键
     * @param mappingFunction 值加载函数
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
     * <p>Lettuce 实现调用 <code>objectMapper.writeValueAsString(value)</code>。</p>
     *
     * @param key 缓存键
     * @param value 缓存值
     */
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

    /**
     * 使指定键的缓存条目失效。
     *
     * <p>Lettuce 实现调用 <code>commands.del(key(key))</code>。</p>
     *
     * @param key 缓存键
     */
    @Override
    public void invalidate(String key) {
        try {
            commands.del(key(key));
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

    // ==================== CAS SPI 实现（基于 Redis Lua 脚本原子操作）====================

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
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>Lettuce 实现调用 <code>key(key)</code>。</p>
     *
     * @param key 缓存键
     * @param expireSeconds 过期时间（秒），0 表示不修改 TTL
     * @param operation CAS 操作回调（基于当前值计算新值）
     * @return 根据执行路径返回<code>null</code>、<code>newValue</code>
     */
    @Override
    public V compareAndSet(String key, long expireSeconds, CASOperation<V, V> operation) {
        String cachedKey = key(key);
        int maxTries = operation.maxRetries() > 0 ? operation.maxRetries() : 16;
        for (int attempt = 0; attempt < maxTries; attempt++) {
            String currentJson = commands.get(cachedKey);
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
            String result = commands.eval(LUA_CAS, ScriptOutputType.INTEGER,
                    new String[]{cachedKey},
                    Objects.isNull(currentJson) ? "" : currentJson, newJson, String.valueOf(expireSeconds));
            if ("1".equals(result)) {
                return newValue;
            }
        }
        return null;
    }

    /**
     * 比较缓存中的现值或版本并尝试原子修改。
     *
     * <p>Lettuce 实现调用 <code>key(key)</code>。</p>
     *
     * @param key 缓存键
     * @param expectedOldValue 期望的旧值（null 表示仅当 key 不存在时写入 = putIfAbsent）
     * @param newValue 新值
     * @return 实际返回<code>"1".equals(result)</code>
     */
    @Override
    public boolean compareAndSet(String key, V expectedOldValue, V newValue) {
        String cachedKey = key(key);
        String expectedJson = Objects.isNull(expectedOldValue) ? "" : serialize(expectedOldValue);
        String newJson = serialize(newValue);
        String result = commands.eval(LUA_CAS, ScriptOutputType.INTEGER, new String[]{cachedKey}, expectedJson, newJson, "0");
        return "1".equals(result);
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
