package io.ddd4j.mq.mqtt.mica;

import io.ddd4j.mq.mqtt.mica.autoconfigure.Ddd4jMicaMqttMQAutoConfiguration;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
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
 * mica-mqtt 发布路径 Testcontainers 冒烟集成测试（Eclipse Mosquitto）。
 * <p>
 * 使用 {@link GenericContainer} + {@code eclipse-mosquitto:2}，端口 1883。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootTest(classes = MicaMqttContainerIT.TestApplication.class)
@EnabledIf("io.ddd4j.mq.mqtt.mica.MicaMqttContainerIT#isDockerAvailable")
class MicaMqttContainerIT {

    private static final int MQTT_PORT = 1883;

    private static final GenericContainer<?> MOSQUITTO = new GenericContainer<>(
            DockerImageName.parse("eclipse-mosquitto:2"))
            .withExposedPorts(MQTT_PORT)
            .waitingFor(Wait.forListeningPort());

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private MqttClientTemplate mqttClientTemplate;

    /**
     * 启动 Mosquitto 容器。
     */
    @BeforeAll
    static void startMosquitto() {
        MOSQUITTO.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String host = MOSQUITTO.getHost();
        int port = MOSQUITTO.getMappedPort(MQTT_PORT);
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "mqtt-mica");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("ddd4j.mq.mica.url", () -> "tcp://" + host + ":" + port);
        registry.add("ddd4j.mq.mica.qos", () -> "1");
        registry.add("mqtt.client.enabled", () -> "true");
        registry.add("mqtt.client.ip", () -> host);
        registry.add("mqtt.client.port", () -> String.valueOf(port));
        registry.add("mqtt.client.client-id", () -> "ddd4j-mica-mqtt-it");
        registry.add("mqtt.client.clean-start", () -> "true");
        registry.add("mqtt.client.reconnect", () -> "true");
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
        assertNotNull(mqttClientTemplate);

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
            org.dromara.mica.mqtt.spring.client.config.MqttClientConfiguration.class,
            io.ddd4j.mq.spring.config.Ddd4jMQPropertiesConfiguration.class,
            Ddd4jMicaMqttMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
