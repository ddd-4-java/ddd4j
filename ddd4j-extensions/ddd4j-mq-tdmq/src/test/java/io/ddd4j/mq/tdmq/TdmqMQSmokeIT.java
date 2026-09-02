package io.ddd4j.mq.tdmq;

import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import io.ddd4j.mq.tdmq.autoconfigure.Ddd4jTdmqMQAutoConfiguration;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDMQ 占位客户端冒烟测试（无 Testcontainers：腾讯云 TDMQ 无可靠开源容器，使用 {@link io.ddd4j.mq.tdmq.client.TdmqClientPlaceholder}）。
 * <p>公共骨架（发布者注入、冒烟发布断言）见 {@link AbstractMqContainerIT}。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        Ddd4jTdmqMQAutoConfiguration.class
})
class TdmqMQSmokeIT extends AbstractMqContainerIT {

    @Autowired
    private TdmqClient tdmqClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerCommonMqProperties(registry, "tdmq", SMOKE_NAMESPACE);
    }

    @Override
    protected void verifyBrokerClient() {
        assertNotNull(tdmqClient);
        assertTrue(tdmqClient.isReady());
    }
}
