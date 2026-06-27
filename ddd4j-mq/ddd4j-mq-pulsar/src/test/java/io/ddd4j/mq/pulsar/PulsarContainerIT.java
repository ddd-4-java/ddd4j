package io.ddd4j.mq.pulsar;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.spring.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.pulsar.autoconfigure.Ddd4jPulsarMQAutoConfiguration;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.pulsar.core.DefaultPulsarClientFactory;
import org.springframework.pulsar.core.DefaultPulsarProducerFactory;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pulsar 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework + spring-pulsar，无 Boot）。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        PulsarContainerIT.PulsarInfrastructureConfiguration.class,
        Ddd4jPulsarMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.pulsar.PulsarContainerIT#isDockerAvailable")
class PulsarContainerIT {

    private static final int PULSAR_BROKER_PORT = 6650;

    private static final GenericContainer<?> PULSAR = new GenericContainer<>(
            DockerImageName.parse("apachepulsar/pulsar:3.3.0"))
            .withExposedPorts(PULSAR_BROKER_PORT, 8080)
            .withCommand("bin/pulsar", "standalone")
            .waitingFor(Wait.forLogMessage(".*messaging service is ready.*", 1));

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private PulsarTemplate<String> pulsarTemplate;

    /**
     * 启动 Pulsar standalone 容器。
     */
    @BeforeAll
    static void startPulsar() {
        PULSAR.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String serviceUrl = "pulsar://" + PULSAR.getHost() + ":" + PULSAR.getMappedPort(PULSAR_BROKER_PORT);
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "pulsar");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("ddd4j.mq.test.pulsar.service-url", () -> serviceUrl);
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
        assertNotNull(pulsarTemplate);

        DemoPublishEvent event = new DemoPublishEvent();
        event.setTopic("smoke");
        event.setTag("ping");
        event.setTenantId("tenant-it");

        assertDoesNotThrow(() -> mqEventPublisher.publish(
                event,
                MQDestination.of("smoke", "ping", "it")));
    }

    /**
     * Pulsar 基础设施（替代 Boot {@code PulsarAutoConfiguration}）。
     */
    @Configuration(proxyBeanMethods = false)
    static class PulsarInfrastructureConfiguration {

        /**
         * 注册 Pulsar 客户端工厂（service-url 由 Testcontainers 动态注入）。
         */
        @Bean
        DefaultPulsarClientFactory pulsarClientFactory(
                @Value("${ddd4j.mq.test.pulsar.service-url}") String serviceUrl) {
            return new DefaultPulsarClientFactory(serviceUrl);
        }

        /**
         * 注册 Pulsar 客户端（供 Producer 工厂使用）。
         */
        @Bean(destroyMethod = "close")
        PulsarClient pulsarClient(DefaultPulsarClientFactory pulsarClientFactory) {
            return pulsarClientFactory.createClient();
        }

        /**
         * 注册 Pulsar Producer 工厂。
         */
        @Bean
        DefaultPulsarProducerFactory<String> pulsarProducerFactory(PulsarClient pulsarClient) {
            return new DefaultPulsarProducerFactory<>(pulsarClient);
        }

        /**
         * 注册 Pulsar 发送模板。
         */
        @Bean
        PulsarTemplate<String> pulsarTemplate(DefaultPulsarProducerFactory<String> pulsarProducerFactory) {
            return new PulsarTemplate<>(pulsarProducerFactory);
        }
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
