package io.ddd4j.boot.cmpt.activemq;

import io.ddd4j.boot.cmpt.activemq.autoconfigure.Ddd4jActiveMQAutoConfiguration;
import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ActiveMQ Artemis 发布路径 Testcontainers 冒烟集成测试（{@code spring-boot-starter-artemis}）。
 * <p>
 * 使用 {@link GenericContainer} + 官方 {@code apache/activemq-artemis} 镜像（Testcontainers 1.20.x 无内置 Artemis 模块）。
 */
@SpringBootTest(classes = ActiveMQContainerIT.TestApplication.class)
@EnabledIf("io.ddd4j.boot.cmpt.activemq.ActiveMQContainerIT#isDockerAvailable")
class ActiveMQContainerIT {

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
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private JmsTemplate jmsTemplate;

    /**
     * 启动 Artemis 容器（避免 testcontainers-junit-jupiter 与 Boot 测试栈版本冲突）。
     */
    @BeforeAll
    static void startArtemis() {
        ARTEMIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String brokerUrl = "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(ARTEMIS_PORT);
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "activemq");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("spring.artemis.mode", () -> "native");
        registry.add("spring.artemis.broker-url", () -> brokerUrl);
        registry.add("spring.artemis.user", () -> ARTEMIS_USER);
        registry.add("spring.artemis.password", () -> ARTEMIS_PASSWORD);
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
        assertNotNull(jmsTemplate);

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
            ArtemisAutoConfiguration.class,
            Ddd4jActiveMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
