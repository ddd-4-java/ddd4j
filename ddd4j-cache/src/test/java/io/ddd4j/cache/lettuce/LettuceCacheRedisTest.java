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

import com.redis.testcontainers.RedisContainer;
import io.ddd4j.core.cache.CacheConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LettuceCache} 的 Redis 集成测试。
 *
 * <p>使用 Testcontainers Redis 模块（com.redis:testcontainers-redis，
 * 模块清单来源 <a href="https://testcontainers.com/modules/">testcontainers.com/modules</a>）
 * 启动真实 Redis 容器，验证缓存 SPI 的 put/get/invalidate/expire 行为。
 *
 * <p>需要本地 Docker 可用；无 Docker 环境时 Testcontainers 会自动跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
class LettuceCacheRedisTest {

    /**
     * 为当前集成测试提供独立 Redis 服务的容器。
     */
    @Container
    private static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7.2-alpine"));

    /**
     * 指向测试 Redis 容器的 Lettuce 客户端。
     */
    private static RedisClient redisClient;
    /**
     * 供测试命令复用的 Redis 连接。
     */
    private static StatefulRedisConnection<String, String> connection;
    /**
     * 测试驱动发送或记录的写入命令。
     */
    private static RedisCommands<String, String> commands;

    /**
     * 连接测试容器并获取用于验证缓存值和过期时间的同步命令接口。
     */
    @BeforeAll
    static void setUp() {
        redisClient = RedisClient.create(REDIS.getRedisURI());
        connection = redisClient.connect();
        commands = connection.sync();
    }

    /**
     * 先关闭测试 Redis 连接，再关闭 Lettuce 客户端。
     */
    @AfterAll
    static void tearDown() {
        if (Objects.nonNull(connection)) {
            connection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }

    /**
     * 连接当前测试 Redis 并建立有效期为 300 秒的缓存。
     *
     * @param name 缓存命名空间
     * @return 可序列化用户测试对象的 Lettuce 缓存
     */
    private LettuceCache<User> newCache(String name) {
        CacheConfig config = CacheConfig.builder(name).expireAfterWriteSeconds(300).build();
        return new LettuceCache<>(commands, config, User.class);
    }

    /**
     * put/getIfPresent 往返：值经 JSON 序列化后可完整读回。
     */
    @Test
    @DisplayName("put/getIfPresent 往返：值经 JSON 序列化后可完整读回")
    void putAndGetRoundTrip() {
        LettuceCache<User> cache = newCache("tc-user");
        User user = new User(42L, "wander");

        cache.put(String.valueOf(user.id), user);

        User cached = cache.getIfPresent("42");
        assertThat(cached).isNotNull();
        assertThat(cached.id).isEqualTo(42L);
        assertThat(cached.name).isEqualTo("wander");
    }

    /**
     * 写入后带 TTL：键具备过期时间。
     */
    @Test
    @DisplayName("写入后带 TTL：键具备过期时间")
    void putAppliesTtl() {
        LettuceCache<User> cache = newCache("tc-ttl");
        cache.put("k1", new User(1L, "ttl"));

        Long ttl = commands.ttl("tc-ttl:k1");
        assertThat(ttl).isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(300L);
    }

    /**
     * invalidate：删除后读取返回 null。
     */
    @Test
    @DisplayName("invalidate：删除后读取返回 null")
    void invalidateRemovesValue() {
        LettuceCache<User> cache = newCache("tc-evict");
        cache.put("k2", new User(2L, "gone"));
        assertThat(cache.getIfPresent("k2")).isNotNull();

        cache.invalidate("k2");

        assertThat(cache.getIfPresent("k2")).isNull();
    }

    /**
     * 键命名空间：不同缓存名互不干扰。
     */
    @Test
    @DisplayName("键命名空间：不同缓存名互不干扰")
    void keyPrefixIsolatesCaches() {
        LettuceCache<User> cacheA = newCache("tc-a");
        LettuceCache<User> cacheB = newCache("tc-b");
        cacheA.put("same", new User(7L, "A"));
        cacheB.put("same", new User(8L, "B"));

        assertThat(cacheA.getIfPresent("same").name).isEqualTo("A");
        assertThat(cacheB.getIfPresent("same").name).isEqualTo("B");
    }

    /**
     * 测试值对象（Jackson 需要公共字段或 getter/setter）。
     */
    public static class User {
        /**
         * 用于校验 JSON 往返的用户标识。
         */
        public Long id;
        /**
         * 用于校验 JSON 往返的用户名称。
         */
        public String name;

        /**
         * 创建字段使用默认初始值的 <code>User</code> 测试对象。
         */
        public User() {
        }

        /**
         * 构造序列化往返所用的用户样本。
         *
         * @param id   样本用户标识
         * @param name 样本用户名称
         */
        public User(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
