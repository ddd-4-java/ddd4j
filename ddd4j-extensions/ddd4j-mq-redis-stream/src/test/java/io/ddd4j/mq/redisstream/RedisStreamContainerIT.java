package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.redisstream.autoconfigure.Ddd4jRedisStreamMQAutoConfiguration;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis Stream 发布路径 Testcontainers 冒烟集成测试（{@code spring-boot-starter-data-redis}）。
 * <p>
 * 使用 {@link GenericContainer} + 官方 {@code redis:7-alpine} 镜像（Testcontainers 无内置 Redis 模块于 1.20.x）。
 */
@SpringBootTest(classes = RedisStreamContainerIT.TestApplication.class)
@EnabledIf("io.ddd4j.mq.redisstream.RedisStreamContainerIT#isDockerAvailable")
class RedisStreamContainerIT {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 启动 Redis 容器（避免 testcontainers-junit-jupiter 与 Boot 测试栈版本冲突）。
     */
    @BeforeAll
    static void startRedis() {
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "redis-stream");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /**
     * Docker 是否可用（Testcontainers 前置条件）。
     */
    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @Test
    void publishShouldNotThrow() {
        assertNotNull(mqEventPublisher);
        assertNotNull(stringRedisTemplate);

        DemoPublishEvent event = new DemoPublishEvent();
        event.setTopic("smoke");
        event.setTag("ping");
        event.setTenantId("tenant-it");

        assertDoesNotThrow(() -> mqEventPublisher.publish(
                event,
                MQDestination.of("smoke", "ping", "it")));
    }

    @SpringBootApplication
    @Import({
            io.ddd4j.mq.config.Ddd4jMQAutoConfiguration.class,
            RedisAutoConfiguration.class,
            Ddd4jRedisStreamMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
