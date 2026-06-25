package io.ddd4j.boot.cmpt.mqtt;

import io.ddd4j.boot.cmpt.mqtt.autoconfigure.Ddd4jMqttMQAutoConfiguration;
import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MQTT 发布路径 Testcontainers 冒烟集成测试（Eclipse Mosquitto）。
 * <p>
 * 使用 {@link GenericContainer} + {@code eclipse-mosquitto:2}，端口 1883。
 */
@SpringBootTest(classes = MqttContainerIT.TestApplication.class)
@EnabledIf("io.ddd4j.boot.cmpt.mqtt.MqttContainerIT#isDockerAvailable")
class MqttContainerIT {

    private static final int MQTT_PORT = 1883;

    private static final GenericContainer<?> MOSQUITTO = new GenericContainer<>(
            DockerImageName.parse("eclipse-mosquitto:2"))
            .withExposedPorts(MQTT_PORT)
            .waitingFor(Wait.forListeningPort());

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private MqttPahoClientFactory mqttPahoClientFactory;

    /**
     * 启动 Mosquitto 容器。
     */
    @BeforeAll
    static void startMosquitto() {
        MOSQUITTO.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String brokerUrl = "tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(MQTT_PORT);
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "mqtt");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("ddd4j.mq.mqtt.url", () -> brokerUrl);
        registry.add("ddd4j.mq.mqtt.clean-session", () -> "true");
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
        assertNotNull(mqttPahoClientFactory);

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
            Ddd4jMqttMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
