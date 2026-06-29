package io.ddd4j.mq.tdmq;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.spring.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.tdmq.autoconfigure.Ddd4jTdmqMQAutoConfiguration;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDMQ 占位客户端冒烟测试（无 Testcontainers：腾讯云 TDMQ 无可靠开源容器，使用 {@link io.ddd4j.mq.tdmq.client.TdmqClientPlaceholder}）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        Ddd4jTdmqMQAutoConfiguration.class
})
class TdmqMQSmokeIT {

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
    void publishShouldNotThrowWithPlaceholderClient() {
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

    static class DemoPublishEvent extends MQEvent {
    }
}
