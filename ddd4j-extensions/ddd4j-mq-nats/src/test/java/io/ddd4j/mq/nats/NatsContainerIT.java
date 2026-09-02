package io.ddd4j.mq.nats;

import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.nats.autoconfigure.Ddd4jNatsMQAutoConfiguration;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import io.nats.client.Connection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * NATS 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework，无 Boot）。
 * <p>公共骨架（发布者注入、Docker 前置、冒烟发布断言）见 {@link AbstractMqContainerIT}。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        Ddd4jNatsMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.test.AbstractMqContainerIT#isDockerAvailable")
class NatsContainerIT extends AbstractMqContainerIT {

    private static final int NATS_PORT = 4222;

    private static final GenericContainer<?> NATS = new GenericContainer<>(DockerImageName.parse("nats:2.10-alpine"))
            .withExposedPorts(NATS_PORT)
            .withCommand("-js", "-m", "8222")
            .waitingFor(Wait.forListeningPort());

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
        registerCommonMqProperties(registry, "nats", SMOKE_NAMESPACE);
        registry.add("ddd4j.mq.nats.servers", () -> servers);
    }

    @Override
    protected void verifyBrokerClient() {
        assertNotNull(natsConnection);
    }
}
