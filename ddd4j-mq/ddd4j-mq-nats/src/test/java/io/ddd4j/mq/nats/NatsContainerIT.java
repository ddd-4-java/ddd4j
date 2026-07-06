package io.ddd4j.mq.nats;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.nats.publisher.NatsMQEventPublisher;
import io.ddd4j.mq.event.MQEventPublisher;
import io.nats.client.Connection;
import io.nats.client.Nats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * NATS 发布路径 Testcontainers 冒烟集成测试（纯 Java，无 Spring）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@EnabledIf("io.ddd4j.mq.nats.NatsContainerIT#isDockerAvailable")
class NatsContainerIT {

    private static final int NATS_PORT = 4222;

    private static final GenericContainer<?> NATS = new GenericContainer<>(DockerImageName.parse("nats:2.10-alpine"))
            .withExposedPorts(NATS_PORT)
            .withCommand("-js", "-m", "8222")
            .waitingFor(Wait.forListeningPort());

    private static Connection natsConnection;
    private static MQEventPublisher mqEventPublisher;

    /**
     * 启动 NATS 容器（JetStream 模式）。
     */
    @BeforeAll
    static void startNats() throws Exception {
        NATS.start();
        String servers = "nats://" + NATS.getHost() + ":" + NATS.getMappedPort(NATS_PORT);
        natsConnection = Nats.connect(servers);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("nats");
        properties.setNamespace("it");
        mqEventPublisher = new NatsMQEventPublisher(natsConnection, properties);
    }

    @AfterAll
    static void stopNats() throws Exception {
        if (Objects.nonNull(natsConnection)) {
            natsConnection.close();
        }
        NATS.stop();
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
                Destination.of("smoke", "ping", "it")));
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
