package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.redisstream.autoconfigure.Ddd4jRedisStreamMQAutoConfiguration;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis Stream 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework，无 Boot）。
 * <p>公共骨架（发布者注入、Docker 前置、冒烟发布断言）见 {@link AbstractMqContainerIT}。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        RedisStreamContainerIT.RedisInfrastructureConfiguration.class,
        Ddd4jRedisStreamMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.test.AbstractMqContainerIT#isDockerAvailable")
class RedisStreamContainerIT extends AbstractMqContainerIT {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

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
        registerCommonMqProperties(registry, "redis-stream", SMOKE_NAMESPACE);
        registry.add("ddd4j.mq.test.redis.host", REDIS::getHost);
        registry.add("ddd4j.mq.test.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
    }

    @Override
    protected void verifyBrokerClient() {
        assertNotNull(stringRedisTemplate);
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
}
