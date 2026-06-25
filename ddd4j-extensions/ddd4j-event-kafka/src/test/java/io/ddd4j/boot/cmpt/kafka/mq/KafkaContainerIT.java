package io.ddd4j.boot.cmpt.kafka.mq;

import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Kafka 发布路径 Testcontainers 冒烟集成测试（{@code spring-kafka} + {@link org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration}）。
 */
@SpringBootTest(classes = KafkaContainerIT.TestApplication.class)
@EnabledIf("io.ddd4j.boot.cmpt.kafka.mq.KafkaContainerIT#isDockerAvailable")
class KafkaContainerIT {

    /** Testcontainers 1.20+ {@link KafkaContainer} 适配官方 {@code apache/kafka} KRaft 镜像 */
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 启动 Kafka 容器（避免 testcontainers-junit-jupiter 与 Boot 测试栈版本冲突）。
     */
    @BeforeAll
    static void startKafka() {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "kafka");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
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
        assertNotNull(kafkaTemplate);

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
            KafkaAutoConfiguration.class,
            Ddd4jKafkaMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
