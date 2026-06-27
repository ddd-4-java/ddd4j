package io.ddd4j.mq.rocket;

import io.ddd4j.mq.rocket.autoconfigure.Ddd4jRocketMQAutoConfiguration;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * RocketMQ 发布路径 Testcontainers 冒烟集成测试（{@code rocketmq-spring-boot-starter}）。
 * <p>
 * 无官方 Testcontainers RocketMQ 模块，使用 {@link FixedHostPortGenericContainer} + {@code apache/rocketmq} 镜像，
 * NameServer/Broker 固定端口映射以避免 broker 注册地址与客户端不可达问题。
 */
@SpringBootTest(classes = RocketMQContainerIT.TestApplication.class)
@EnabledIf("io.ddd4j.mq.rocket.RocketMQContainerIT#isDockerAvailable")
class RocketMQContainerIT {

    private static final String ROCKETMQ_IMAGE = "apache/rocketmq:5.3.1";
    private static final Network ROCKETMQ_NETWORK = Network.newNetwork();

    private static final FixedHostPortGenericContainer<?> NAMESRV = new FixedHostPortGenericContainer<>(ROCKETMQ_IMAGE)
            .withNetwork(ROCKETMQ_NETWORK)
            .withNetworkAliases("namesrv")
            .withFixedExposedPort(9876, 9876)
            .withCommand("sh", "mqnamesrv")
            .waitingFor(Wait.forListeningPort());

    private static final FixedHostPortGenericContainer<?> BROKER = new FixedHostPortGenericContainer<>(ROCKETMQ_IMAGE)
            .withNetwork(ROCKETMQ_NETWORK)
            .withFixedExposedPort(10911, 10911)
            .withFixedExposedPort(10909, 10909)
            .dependsOn(NAMESRV)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("rocketmq/broker-it.conf"),
                    "/home/rocketmq/rocketmq-5.3.1/conf/broker-it.conf")
            .withCommand("sh", "mqbroker", "-n", "namesrv:9876", "-c", "/home/rocketmq/rocketmq-5.3.1/conf/broker-it.conf")
            .waitingFor(Wait.forLogMessage(".*boot success.*", 1));

    private static final String SMOKE_TOPIC = "smoke";

    private static volatile boolean containersStarted;

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 在 Spring 上下文启动前拉起容器并预创建 topic（{@link DynamicPropertySource} 早于上下文刷新）。
     */
    private static synchronized void ensureContainersStarted() {
        if (containersStarted) {
            return;
        }
        try {
            NAMESRV.start();
            BROKER.start();
            ensureSmokeTopic();
            containersStarted = true;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to start RocketMQ Testcontainers", ex);
        }
    }

    /**
     * 释放固定端口，避免同机连续 {@code mvn verify} 时端口占用。
     */
    @AfterAll
    static void stopRocketMQ() {
        if (BROKER.isRunning()) {
            BROKER.stop();
        }
        if (NAMESRV.isRunning()) {
            NAMESRV.stop();
        }
    }

    /**
     * 预创建冒烟 topic（NameServer 需已有路由；Broker 就绪后重试 mqadmin）。
     */
    private static void ensureSmokeTopic() throws Exception {
        String createTopicCmd = "cd /home/rocketmq/rocketmq-5.3.1 && bin/mqadmin updateTopic -n namesrv:9876 -t "
                + SMOKE_TOPIC + " -b 127.0.0.1:10911";
        String verifyRouteCmd = "cd /home/rocketmq/rocketmq-5.3.1 && bin/mqadmin topicRoute -n namesrv:9876 -t "
                + SMOKE_TOPIC;
        IllegalStateException lastError = null;
        Thread.sleep(2_000L);
        for (int attempt = 1; attempt <= 15; attempt++) {
            Container.ExecResult createResult = BROKER.execInContainer("sh", "-c", createTopicCmd);
            Container.ExecResult routeResult = BROKER.execInContainer("sh", "-c", verifyRouteCmd);
            if (createResult.getExitCode() == 0 && routeResult.getExitCode() == 0) {
                return;
            }
            lastError = new IllegalStateException(
                    "smoke topic not ready (attempt " + attempt + "), create="
                            + createResult.getExitCode() + ", route=" + routeResult.getExitCode()
                            + ", stderr=" + createResult.getStderr() + routeResult.getStderr());
            Thread.sleep(1_000L);
        }
        throw lastError;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        ensureContainersStarted();
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "rocket");
        // RocketMQ topic 不允许 '.'，冒烟测试使用无 namespace 的单一 topic 段
        registry.add("ddd4j.mq.namespace", () -> "");
        registry.add("rocketmq.name-server", () -> "127.0.0.1:9876");
        registry.add("rocketmq.producer.group", () -> "it-producer-group");
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
        assertNotNull(rocketMQTemplate);

        DemoPublishEvent event = new DemoPublishEvent();
        event.setTopic(SMOKE_TOPIC);
        event.setTag("ping");
        event.setTenantId("tenant-it");

        assertDoesNotThrow(() -> mqEventPublisher.publish(
                event,
                MQDestination.of(SMOKE_TOPIC, "ping", "")));
    }

    @SpringBootApplication
    @Import({
            io.ddd4j.mq.spring.config.Ddd4jMQPropertiesConfiguration.class,
            RocketMQAutoConfiguration.class,
            Ddd4jRocketMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
