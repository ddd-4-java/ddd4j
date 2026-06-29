package io.ddd4j.mq.redisstream;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.redisstream.autoconfigure.Ddd4jRedisStreamMQAutoConfiguration;
import io.ddd4j.mq.spring.config.Ddd4jMQPropertiesConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis Stream 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework，无 Boot）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        RedisStreamContainerIT.RedisInfrastructureConfiguration.class,
        Ddd4jRedisStreamMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.redisstream.RedisStreamContainerIT#isDockerAvailable")
class RedisStreamContainerIT {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 启动 Redis 容器。
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
        registry.add("ddd4j.mq.test.redis.host", REDIS::getHost);
        registry.add("ddd4j.mq.test.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
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

    /**
     * Redis 基础设施（替代 Boot {@code RedisAutoConfiguration}）。
     */
    @Configuration(proxyBeanMethods = false)
    static class RedisInfrastructureConfiguration {

        /**
         * 注册 Lettuce 连接工厂。
         */
        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory redisConnectionFactory(
                @Value("${ddd4j.mq.test.redis.host}") String host,
                @Value("${ddd4j.mq.test.redis.port}") int port) {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
            LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
            factory.afterPropertiesSet();
            return factory;
        }

        /**
         * 注册 String Redis 模板。
         */
        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
            return new StringRedisTemplate(redisConnectionFactory);
        }
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
