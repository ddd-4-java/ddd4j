package io.ddd4j.mq.pulsar;

import io.ddd4j.mq.pulsar.autoconfigure.Ddd4jPulsarMQAutoConfiguration;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pulsar 发布路径 Testcontainers 冒烟集成测试（{@code spring-boot-starter-pulsar}）。
 * <p>
 * 无 Testcontainers 1.20.x 官方 Pulsar 模块，使用 {@link GenericContainer} + {@code apachepulsar/pulsar} standalone。
 */
@SpringBootTest(classes = PulsarContainerIT.TestApplication.class)
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
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "pulsar");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("spring.pulsar.client.service-url", () -> serviceUrl);
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
        assertNotNull(pulsarClient);

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
            PulsarAutoConfiguration.class,
            Ddd4jPulsarMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
