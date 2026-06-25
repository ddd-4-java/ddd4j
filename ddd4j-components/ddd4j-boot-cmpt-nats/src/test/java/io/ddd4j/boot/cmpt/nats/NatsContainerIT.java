package io.ddd4j.boot.cmpt.nats;

import io.ddd4j.boot.cmpt.nats.autoconfigure.Ddd4jNatsMQAutoConfiguration;
import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.nats.client.Connection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * NATS 发布路径 Testcontainers 冒烟集成测试（{@code jnats}，无官方 Spring Boot Starter）。
 * <p>
 * 使用 {@link GenericContainer} + {@code nats:2.10-alpine} 并启用 JetStream（{@code -js}）。
 */
@SpringBootTest(classes = NatsContainerIT.TestApplication.class)
@EnabledIf("io.ddd4j.boot.cmpt.nats.NatsContainerIT#isDockerAvailable")
class NatsContainerIT {

    private static final int NATS_PORT = 4222;

    private static final GenericContainer<?> NATS = new GenericContainer<>(DockerImageName.parse("nats:2.10-alpine"))
            .withExposedPorts(NATS_PORT)
            .withCommand("-js", "-m", "8222")
            .waitingFor(Wait.forListeningPort());

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private Connection natsConnection;

    /**
     * 启动 NATS 容器（JetStream 模式）。
     */
    @BeforeAll
    static void startNats() {
        NATS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String servers = "nats://" + NATS.getHost() + ":" + NATS.getMappedPort(NATS_PORT);
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "nats");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("ddd4j.mq.nats.servers", () -> servers);
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
        assertNotNull(natsConnection);

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
            io.ddd4j.boot.mq.config.Ddd4jMQAutoConfiguration.class,
            Ddd4jNatsMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
