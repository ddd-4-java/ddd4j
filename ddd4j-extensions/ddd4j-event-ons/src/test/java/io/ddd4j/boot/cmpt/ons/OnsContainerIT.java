package io.ddd4j.boot.cmpt.ons;

import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.boot.cmpt.ons.autoconfigure.Ddd4jOnsMQAutoConfiguration;
import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 阿里云 ONS 发布路径 Testcontainers 冒烟集成测试（{@code ons-client}）。
 * <p>
 * <strong>当前 {@link Disabled}：</strong>{@code ons-client 2.0.x} 内嵌 RocketMQ 5 gRPC 客户端，经 gRPC/TLS
 * 拉取 Topic 路由；开源 Testcontainers 仅 NameServer+Broker（无 Proxy）无法满足 ALPN 握手，发布报
 * {@code FETCH_TOPIC_ROUTE_FAILURE / Failed ALPN negotiation}。待 RocketMQ Proxy 容器方案稳定后启用。
 * <p>
 * 参考：{@link io.ddd4j.boot.cmpt.rocket.RocketMQContainerIT}（原生 {@code rocketmq-spring} remoting 可测）。
 */
@Disabled("Blocker: ons-client 2.0.x requires RocketMQ 5 gRPC Proxy; NameServer+Broker Testcontainers insufficient")
@SpringBootTest(classes = OnsContainerIT.TestApplication.class)
class OnsContainerIT {

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private Producer onsProducer;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "ons");
        registry.add("ddd4j.mq.namespace", () -> "");
        registry.add("ddd4j.mq.ons.namesrv-addr", () -> "127.0.0.1:9876");
        registry.add("ddd4j.mq.ons.access-key", () -> "it-access-key");
        registry.add("ddd4j.mq.ons.secret-key", () -> "it-secret-key");
        registry.add("ddd4j.mq.ons.producer-group", () -> "it-ons-producer-group");
    }

    /**
     * 冒烟：{@link MQEventPublisher#publish} 不抛异常（需真实 ONS / RocketMQ Proxy 端点时启用本 IT）。
     */
    @Test
    void publishShouldNotThrow() {
        assertNotNull(mqEventPublisher);
        assertNotNull(onsProducer);

        DemoPublishEvent event = new DemoPublishEvent();
        event.setTopic("smoke");
        event.setTag("ping");
        event.setTenantId("tenant-it");

        assertDoesNotThrow(() -> mqEventPublisher.publish(
                event,
                MQDestination.of("smoke", "ping", "")));
    }

    @SpringBootApplication
    @Import({
            io.ddd4j.boot.mq.config.Ddd4jMQAutoConfiguration.class,
            Ddd4jOnsMQAutoConfiguration.class
    })
    static class TestApplication {
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
