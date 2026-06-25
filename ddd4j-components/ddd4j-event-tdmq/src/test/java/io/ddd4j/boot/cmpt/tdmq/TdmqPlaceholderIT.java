package io.ddd4j.boot.cmpt.tdmq;

import io.ddd4j.boot.cmpt.tdmq.autoconfigure.Ddd4jTdmqMQAutoConfiguration;
import io.ddd4j.boot.cmpt.tdmq.client.TdmqClient;
import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 腾讯云 TDMQ 发布路径冒烟集成测试（{@link io.ddd4j.boot.cmpt.tdmq.client.TdmqClientPlaceholder} 进程内总线）。
 * <p>
 * 当前模块仅有占位客户端，无真实网络 SDK；无需 Docker / Testcontainers。
 */
@SpringBootTest(classes = TdmqPlaceholderIT.TestApplication.class)
class TdmqPlaceholderIT {

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private TdmqClient tdmqClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "tdmq");
        registry.add("ddd4j.mq.namespace", () -> "it");
    }

    @Test
    void publishShouldNotThrow() {
        assertNotNull(mqEventPublisher);
        assertNotNull(tdmqClient);
        assertTrue(tdmqClient.isReady());

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
            Ddd4jTdmqMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
