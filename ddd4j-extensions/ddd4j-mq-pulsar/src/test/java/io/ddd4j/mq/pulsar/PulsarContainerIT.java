package io.ddd4j.mq.pulsar;

import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.pulsar.autoconfigure.Ddd4jPulsarMQAutoConfiguration;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pulsar 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework + 原生 PulsarClient）。
 * <p>公共骨架（发布者注入、Docker 前置、冒烟发布断言）见 {@link AbstractMqContainerIT}。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        PulsarContainerIT.PulsarInfrastructureConfiguration.class,
        Ddd4jPulsarMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.test.AbstractMqContainerIT#isDockerAvailable")
class PulsarContainerIT extends AbstractMqContainerIT {

    private static final int PULSAR_BROKER_PORT = 6650;

    private static final GenericContainer<?> PULSAR = new GenericContainer<>(
            DockerImageName.parse("apachepulsar/pulsar:3.3.0"))
            .withExposedPorts(PULSAR_BROKER_PORT, 8080)
            .withCommand("bin/pulsar", "standalone")
            .waitingFor(Wait.forLogMessage(".*messaging service is ready.*", 1));

    @Autowired
    private PulsarClient pulsarClient;

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
        registerCommonMqProperties(registry, "pulsar", SMOKE_NAMESPACE);
        registry.add("ddd4j.mq.test.pulsar.service-url", () -> serviceUrl);
    }

    @Override
    protected void verifyBrokerClient() {
        assertNotNull(pulsarClient);
    }

    /**
     * Pulsar 基础设施（替代 Boot {@code PulsarAutoConfiguration}）。
     */
    @Configuration(proxyBeanMethods = false)
    static class PulsarInfrastructureConfiguration {

        /**
         * 注册 Pulsar 客户端（service-url 由 Testcontainers 动态注入）。
         */
        @Bean(destroyMethod = "close")
        PulsarClient pulsarClient(@Value("${ddd4j.mq.test.pulsar.service-url}") String serviceUrl) throws Exception {
            return PulsarClient.builder().serviceUrl(serviceUrl).build();
        }
    }
}
