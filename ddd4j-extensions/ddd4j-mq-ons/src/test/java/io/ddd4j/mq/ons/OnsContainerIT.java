package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.ons.autoconfigure.Ddd4jOnsMQAutoConfiguration;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 阿里云 ONS Testcontainers 集成测试占位（纯 Spring Framework，无 Boot）。
 * <p>
 * <strong>当前 {@link Disabled}：</strong>{@code ons-client 2.0.x} 内嵌 RocketMQ 5 gRPC 客户端，开源 Testcontainers
 * 仅 NameServer+Broker（无 Proxy）无法满足 ALPN 握手。待 RocketMQ Proxy 容器方案稳定后启用。
 * 公共骨架（发布者注入、冒烟发布断言）见 {@link AbstractMqContainerIT}。
 */
@Disabled("Blocker: ons-client 2.0.x requires RocketMQ 5 gRPC Proxy; NameServer+Broker Testcontainers insufficient")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        Ddd4jOnsMQAutoConfiguration.class
})
class OnsContainerIT extends AbstractMqContainerIT {

    @Autowired
    private Producer onsProducer;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerCommonMqProperties(registry, "ons", "");
        registry.add("ddd4j.mq.ons.namesrv-addr", () -> "127.0.0.1:9876");
        registry.add("ddd4j.mq.ons.access-key", () -> "it-access-key");
        registry.add("ddd4j.mq.ons.secret-key", () -> "it-secret-key");
        registry.add("ddd4j.mq.ons.producer-group", () -> "it-ons-producer-group");
    }

    @Override
    protected void verifyBrokerClient() {
        assertNotNull(onsProducer);
    }

    @Override
    protected String smokeNamespace() {
        // ONS 复用 RocketMQ topic 命名约束：不允许 '.'，冒烟使用无 namespace 的单一 topic 段
        return "";
    }
}
