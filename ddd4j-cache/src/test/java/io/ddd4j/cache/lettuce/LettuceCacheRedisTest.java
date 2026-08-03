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

    @Container
    private static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7.2-alpine"));

    private static RedisClient redisClient;
    private static StatefulRedisConnection<String, String> connection;
    private static RedisCommands<String, String> commands;

    @BeforeAll
    static void setUp() {
        redisClient = RedisClient.create(REDIS.getRedisURI());
        connection = redisClient.connect();
        commands = connection.sync();
    }

    @AfterAll
    static void tearDown() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    private LettuceCache<User> newCache(String name) {
        CacheConfig config = CacheConfig.builder(name).expireAfterWriteSeconds(300).build();
        return new LettuceCache<>(commands, config, User.class);
    }

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

    @Test
    @DisplayName("写入后带 TTL：键具备过期时间")
    void putAppliesTtl() {
        LettuceCache<User> cache = newCache("tc-ttl");
        cache.put("k1", new User(1L, "ttl"));

        Long ttl = commands.ttl("tc-ttl:k1");
        assertThat(ttl).isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(300L);
    }

    @Test
    @DisplayName("invalidate：删除后读取返回 null")
    void invalidateRemovesValue() {
        LettuceCache<User> cache = newCache("tc-evict");
        cache.put("k2", new User(2L, "gone"));
        assertThat(cache.getIfPresent("k2")).isNotNull();

        cache.invalidate("k2");

        assertThat(cache.getIfPresent("k2")).isNull();
    }

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
        public Long id;
        public String name;

        public User() {
        }

        public User(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
