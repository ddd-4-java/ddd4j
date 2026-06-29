package io.ddd4j.mq.mqtt;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.mqtt.autoconfigure.Ddd4jMqttMQAutoConfiguration;
import io.ddd4j.mq.mqtt.config.Ddd4jMqttProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.spring.config.Ddd4jMQPropertiesConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
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
 * MQTT 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework，无 Boot）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        MqttContainerIT.MqttPropertiesConfiguration.class,
        Ddd4jMqttMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.mqtt.MqttContainerIT#isDockerAvailable")
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

    /**
     * MQTT 模块属性绑定（替代 Boot {@code @EnableConfigurationProperties}）。
     */
    @Configuration(proxyBeanMethods = false)
    static class MqttPropertiesConfiguration {

        /**
         * 从 Environment 绑定 {@code ddd4j.mq.mqtt.*}。
         */
        @Bean
        Ddd4jMqttProperties ddd4jMqttProperties(Environment environment) {
            Ddd4jMqttProperties properties = new Ddd4jMqttProperties();
            properties.setUrl(environment.getProperty("ddd4j.mq.mqtt.url", "tcp://127.0.0.1:1883"));
            properties.setCleanSession(Boolean.parseBoolean(
                    environment.getProperty("ddd4j.mq.mqtt.clean-session", "true")));
            return properties;
        }
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
