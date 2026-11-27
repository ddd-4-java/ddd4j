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

    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb Redis \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    private static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7.2-alpine"));

    /** \u6307\u5411\u6d4b\u8bd5 Redis \u5bb9\u5668\u7684 Lettuce \u5ba2\u6237\u7aef\u3002 */
    private static RedisClient redisClient;
    /** \u4f9b\u6d4b\u8bd5\u547d\u4ee4\u590d\u7528\u7684 Redis \u8fde\u63a5\u3002 */
    private static StatefulRedisConnection<String, String> connection;
    /** \u6d4b\u8bd5\u9a71\u52a8\u53d1\u9001\u6216\u8bb0\u5f55\u7684\u5199\u5165\u547d\u4ee4\u3002 */
    private static RedisCommands<String, String> commands;

    /** \u8fde\u63a5\u6d4b\u8bd5\u5bb9\u5668\u5e76\u83b7\u53d6\u7528\u4e8e\u9a8c\u8bc1\u7f13\u5b58\u503c\u548c\u8fc7\u671f\u65f6\u95f4\u7684\u540c\u6b65\u547d\u4ee4\u63a5\u53e3\u3002 */
    @BeforeAll
    static void setUp() {
        redisClient = RedisClient.create(REDIS.getRedisURI());
        connection = redisClient.connect();
        commands = connection.sync();
    }

    /** \u5148\u5173\u95ed\u6d4b\u8bd5 Redis \u8fde\u63a5\uff0c\u518d\u5173\u95ed Lettuce \u5ba2\u6237\u7aef\u3002 */
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
     * \u8fde\u63a5\u5f53\u524d\u6d4b\u8bd5 Redis \u5e76\u5efa\u7acb\u6709\u6548\u671f\u4e3a 300 \u79d2\u7684\u7f13\u5b58\u3002
     * @param name \u7f13\u5b58\u547d\u540d\u7a7a\u95f4
     * @return \u53ef\u5e8f\u5217\u5316\u7528\u6237\u6d4b\u8bd5\u5bf9\u8c61\u7684 Lettuce \u7f13\u5b58
     */
    private LettuceCache<User> newCache(String name) {
        CacheConfig config = CacheConfig.builder(name).expireAfterWriteSeconds(300).build();
        return new LettuceCache<>(commands, config, User.class);
    }

    /** put/getIfPresent \u5f80\u8fd4\uff1a\u503c\u7ecf JSON \u5e8f\u5217\u5316\u540e\u53ef\u5b8c\u6574\u8bfb\u56de\u3002 */
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

    /** \u5199\u5165\u540e\u5e26 TTL\uff1a\u952e\u5177\u5907\u8fc7\u671f\u65f6\u95f4\u3002 */
    @Test
    @DisplayName("写入后带 TTL：键具备过期时间")
    void putAppliesTtl() {
        LettuceCache<User> cache = newCache("tc-ttl");
        cache.put("k1", new User(1L, "ttl"));

        Long ttl = commands.ttl("tc-ttl:k1");
        assertThat(ttl).isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(300L);
    }

    /** invalidate\uff1a\u5220\u9664\u540e\u8bfb\u53d6\u8fd4\u56de null\u3002 */
    @Test
    @DisplayName("invalidate：删除后读取返回 null")
    void invalidateRemovesValue() {
        LettuceCache<User> cache = newCache("tc-evict");
        cache.put("k2", new User(2L, "gone"));
        assertThat(cache.getIfPresent("k2")).isNotNull();

        cache.invalidate("k2");

        assertThat(cache.getIfPresent("k2")).isNull();
    }

    /** \u952e\u547d\u540d\u7a7a\u95f4\uff1a\u4e0d\u540c\u7f13\u5b58\u540d\u4e92\u4e0d\u5e72\u6270\u3002 */
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
        /** \u7528\u4e8e\u6821\u9a8c JSON \u5f80\u8fd4\u7684\u7528\u6237\u6807\u8bc6\u3002 */
        public Long id;
        /** \u7528\u4e8e\u6821\u9a8c JSON \u5f80\u8fd4\u7684\u7528\u6237\u540d\u79f0\u3002 */
        public String name;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>User</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        public User() {
        }

        /**
         * \u6784\u9020\u5e8f\u5217\u5316\u5f80\u8fd4\u6240\u7528\u7684\u7528\u6237\u6837\u672c\u3002
         * @param id \u6837\u672c\u7528\u6237\u6807\u8bc6
         * @param name \u6837\u672c\u7528\u6237\u540d\u79f0
         */
        public User(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
