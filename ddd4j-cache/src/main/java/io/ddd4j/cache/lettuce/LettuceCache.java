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

    /**
     * 在 Redis 服务端比较当前缓存值并按过期配置原子更新的脚本。
     */
    private static final String LUA_CAS =
            "local cur = redis.call('GET', KEYS[1]) " +
                    "if cur == ARGV[1] then " +
                    "  redis.call('SET', KEYS[1], ARGV[2]) " +
                    "  if tonumber(ARGV[3]) > 0 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end " +
                    "  return 1 " +
                    "end " +
                    "return 0";
    /**
     * 执行 Redis 命令的 Lettuce 同步接口。
     */
    private final RedisCommands<String, String> commands;
    /**
     * 缓存写入时使用的默认生存时长。
     */
    private final Duration expireDuration;
    /**
     * 反序列化缓存条目时使用的值类型。
     */
    private final Class<V> valueType;
    /**
     * 负责在 Redis 或 Memcached 中存储 JSON 值的映射器。
     */
    private final ObjectMapper objectMapper;
    /**
     * 用于隔离当前缓存命名空间的键前缀。
     */
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
     * 使用指定 ObjectMapper 工厂创建 Lettuce 缓存。
     *
     * @param commands            Redis 命令接口
     * @param config              缓存配置
     * @param valueType           缓存值类型
     * @param objectMapperFactory ObjectMapper 工厂
     */
    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType, Function<String, ObjectMapper> objectMapperFactory) {
        this(commands, config, valueType, objectMapperFactory.apply(valueType.getName()));
    }

    /**
     * 构造 Lettuce 缓存（默认 ObjectMapper）。
     *
     * @param commands  Redis 命令接口
     * @param config    缓存配置
     * @param valueType 缓存值类型
     */
    public LettuceCache(RedisCommands<String, String> commands, CacheConfig config, Class<V> valueType) {
        this(commands, config, valueType, JsonMapper.builder().build());
    }

    /**
     * 为业务键添加当前缓存命名空间前缀。
     *
     * @param key 缓存键
     * @return 写入后端使用的缓存键
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
     * @param key             缓存键
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
     * @param key   缓存键
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

    /**
     * 保留后端现有键。调用方需通过明确的键逐个失效。
     */
    @Override
    public void invalidateAll() {
        // Redis 不建议 flushAll，需通过 key 前缀逐个删除
    }

    /**
     * 报告当前后端的缓存条目数量是否可用。
     *
     * @return 固定返回 -1，表示此适配器不估算条目数
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
     * @param key           缓存键
     * @param expireSeconds 过期时间（秒），0 表示不修改 TTL
     * @param operation     CAS 操作回调（基于当前值计算新值）
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
     * @param key              缓存键
     * @param expectedOldValue 期望的旧值（null 表示仅当 key 不存在时写入 = putIfAbsent）
     * @param newValue         新值
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
     * 保留字符串值原样，将其他非空值编码为 JSON。
     *
     * @param value 待写入的缓存值
     * @return 已编码的字符串，空值或编码失败时返回空字符串
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
     * 按照适配器的值类型反序列化缓存文本。
     *
     * @param json 从缓存后端读出的字符串
     * @return 解码后的缓存值，输入为空或解码失败时返回空
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
