package io.ddd4j.mq.activemq;

import io.ddd4j.mq.activemq.autoconfigure.Ddd4jActiveMQAutoConfiguration;
import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import javax.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ActiveMQ Artemis 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework，无 Boot）。
 * <p>公共骨架（发布者注入、Docker 前置、冒烟发布断言）见 {@link AbstractMqContainerIT}。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        ActiveMQContainerIT.ArtemisInfrastructureConfiguration.class,
        Ddd4jActiveMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.test.AbstractMqContainerIT#isDockerAvailable")
class ActiveMQContainerIT extends AbstractMqContainerIT {

    private static final String ARTEMIS_USER = "artemis";
    private static final String ARTEMIS_PASSWORD = "artemis";
    private static final int ARTEMIS_PORT = 61616;

    private static final GenericContainer<?> ARTEMIS = new GenericContainer<>(
            DockerImageName.parse("apache/activemq-artemis:2.37.0"))
            .withExposedPorts(ARTEMIS_PORT)
            .withEnv("ARTEMIS_USER", ARTEMIS_USER)
            .withEnv("ARTEMIS_PASSWORD", ARTEMIS_PASSWORD)
            .waitingFor(Wait.forListeningPort());

    @Autowired
    private JmsTemplate jmsTemplate;

    /**
     * 启动 Artemis 容器。
     */
    @BeforeAll
    static void startArtemis() {
        ARTEMIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String brokerUrl = "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(ARTEMIS_PORT);
        registerCommonMqProperties(registry, "activemq", SMOKE_NAMESPACE);
        registry.add("ddd4j.mq.test.artemis.broker-url", () -> brokerUrl);
        registry.add("ddd4j.mq.test.artemis.user", () -> ARTEMIS_USER);
        registry.add("ddd4j.mq.test.artemis.password", () -> ARTEMIS_PASSWORD);
    }

    @Override
    protected void verifyBrokerClient() {
        assertNotNull(jmsTemplate);
    }

    /**
     * Artemis JMS 基础设施（替代 Boot {@code ArtemisAutoConfiguration}）。
     */
    @Configuration(proxyBeanMethods = false)
    static class ArtemisInfrastructureConfiguration {

        /**
         * 注册 Artemis {@link ConnectionFactory}。
         */
        @Bean
        ConnectionFactory connectionFactory(
                @Value("${ddd4j.mq.test.artemis.broker-url}") String brokerUrl,
                @Value("${ddd4j.mq.test.artemis.user}") String user,
                @Value("${ddd4j.mq.test.artemis.password}") String password) {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            factory.setUser(user);
            factory.setPassword(password);
            return factory;
        }

        /**
         * 注册 JMS 发送模板。
         */
        @Bean
        JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
            return new JmsTemplate(connectionFactory);
        }

        /**
         * 消费端点注册表（ActiveMQ 消费编排依赖）。
         */
        @Bean
        JmsListenerEndpointRegistry jmsListenerEndpointRegistry() {
            return new JmsListenerEndpointRegistry();
        }
    }
}
