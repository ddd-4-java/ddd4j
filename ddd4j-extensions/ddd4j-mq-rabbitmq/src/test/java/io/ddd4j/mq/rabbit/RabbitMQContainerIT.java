package io.ddd4j.mq.rabbit;

import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.rabbit.autoconfigure.Ddd4jRabbitMQAutoConfiguration;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.RabbitMQContainer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * RabbitMQ 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework，无 Boot）。
 * <p>公共骨架（发布者注入、Docker 前置、冒烟发布断言）见 {@link AbstractMqContainerIT}。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        RabbitMQContainerIT.RabbitInfrastructureConfiguration.class,
        Ddd4jRabbitMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.test.AbstractMqContainerIT#isDockerAvailable")
class RabbitMQContainerIT extends AbstractMqContainerIT {

    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 启动 RabbitMQ 容器。
     */
    @BeforeAll
    static void startContainer() {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerCommonMqProperties(registry, "rabbit", SMOKE_NAMESPACE);
        registry.add("ddd4j.mq.test.rabbit.host", RABBIT::getHost);
        registry.add("ddd4j.mq.test.rabbit.port", () -> String.valueOf(RABBIT.getAmqpPort()));
        registry.add("ddd4j.mq.test.rabbit.username", RABBIT::getAdminUsername);
        registry.add("ddd4j.mq.test.rabbit.password", RABBIT::getAdminPassword);
    }

    @Override
    protected void verifyBrokerClient() {
        assertNotNull(rabbitTemplate);
    }

    /**
     * RabbitMQ 基础设施（替代 Boot Rabbit 自动配置）。
     */
    @Configuration(proxyBeanMethods = false)
    static class RabbitInfrastructureConfiguration {

        /**
         * 注册 AMQP 连接工厂。
         */
        @Bean
        ConnectionFactory connectionFactory(
                @Value("${ddd4j.mq.test.rabbit.host}") String host,
                @Value("${ddd4j.mq.test.rabbit.port}") int port,
                @Value("${ddd4j.mq.test.rabbit.username}") String username,
                @Value("${ddd4j.mq.test.rabbit.password}") String password) {
            CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
            factory.setUsername(username);
            factory.setPassword(password);
            return factory;
        }

        /**
         * 注册 Rabbit 发送模板。
         */
        @Bean
        RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
            return new RabbitTemplate(connectionFactory);
        }

        /**
         * 消费端点注册表（Rabbit 消费编排依赖）。
         */
        @Bean
        RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry() {
            return new RabbitListenerEndpointRegistry();
        }
    }
}
