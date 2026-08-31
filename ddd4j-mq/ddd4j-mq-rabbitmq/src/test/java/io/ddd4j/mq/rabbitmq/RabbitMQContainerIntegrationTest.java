package io.ddd4j.mq.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RabbitMQ testcontainers 集成测试：使用官方 {@link RabbitMQContainer} 启动真实 broker，
 * 验证 ddd4j 的 RabbitMQProperties / RabbitMQClient 在真实 AMQP broker 上的兼容性。
 *
 * <p>本测试覆盖：
 * <ul>
 *   <li>RabbitMQProperties 连接真实 RabbitMQ 镜像（验证 host/port/credential 配置正确）</li>
 *   <li>amqp-client 5.x 与 RabbitMQ 3.13-management 协议互通</li>
 *   <li>messageId 与自定义 header 在 broker 上保留完整（ddd4j 消息头兼容）</li>
 * </ul>
 *
 * <p>RabbitMQClient.initConsumer/initProducer 内部依赖 MQListener 反射——本测试不强行跑全链路，
 * 而是聚焦 broker 兼容性验证，确保 ddd4j 字段不会在传输中被截断。
 *
 * <p>在 CI {@code infrastructure-integration} job（Docker daemon）下运行；本地需 Docker 环境。
 */
@Testcontainers(disabledWithoutDocker = true)
class RabbitMQContainerIntegrationTest {

    private static final String EXCHANGE = "ddd4j.it.rabbit";
    private static final String QUEUE = "ddd4j.it.rabbit.verify";

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    @Test
    void rabbitMQPropertiesConnectsToContainerBroker() throws Exception {
        RabbitMQProperties properties = brokerProperties();

        try (Connection connection = properties.connectionFactory().newConnection();
             Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(EXCHANGE, "direct", true);
            channel.queueDeclare(QUEUE, true, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, "");

            String msgId = "IT-RABBIT-" + System.nanoTime();
            String tag = "verify-tag";
            String tenantId = "tenant-it";
            Map<String, Object> headers = new HashMap<>();
            headers.put("ddd4j-message-id", msgId);
            headers.put("ddd4j-message-tag", tag);
            headers.put("ddd4j-tenant-id", tenantId);

            AMQP.BasicProperties sent = new AMQP.BasicProperties.Builder()
                    .messageId(msgId)
                    .contentType("application/json")
                    .headers(headers)
                    .build();
            byte[] body = ("{\"topic\":\"" + EXCHANGE + "\",\"msgId\":\"" + msgId + "\"}")
                    .getBytes(StandardCharsets.UTF_8);

            channel.basicPublish(EXCHANGE, "", sent, body);
            GetResponse received = channel.basicGet(QUEUE, true);
            assertNotNull(received, "消息应能从真实 RabbitMQ broker 消费");

            // 验证 ddd4j 消息头（带 . 与 -）与 broker AMQP 协议兼容
            assertEquals(msgId, received.getProps().getMessageId(),
                    "messageId 完整保留");
            Map<String, Object> gotHeaders = received.getProps().getHeaders();
            assertEquals(msgId, gotHeaders.get("ddd4j-message-id").toString());
            assertEquals(tag, gotHeaders.get("ddd4j-message-tag").toString());
            assertEquals(tenantId, gotHeaders.get("ddd4j-tenant-id").toString());
            assertEquals(body.length, received.getBody().length,
                    "消息体长度一致（未被截断）");
        }
    }

    @Test
    void rabbitMQContainerExposesStandardAmqpPort() throws Exception {
        assertNotNull(RABBIT.getAmqpPort(), "AMQP 端口应就绪");
        assertNotNull(RABBIT.getHost(), "AMQP 主机应就绪");
        // 验证容器确实启动（默认端口 5672 或映射到随机端口）
        assertTrue(RABBIT.getAmqpPort() > 0);
    }

    private static RabbitMQProperties brokerProperties() {
        RabbitMQProperties properties = new RabbitMQProperties();
        properties.setHost(RABBIT.getHost());
        properties.setPort(RABBIT.getAmqpPort());
        properties.setUsername(RABBIT.getAdminUsername());
        properties.setPassword(RABBIT.getAdminPassword());
        properties.setVirtualHost("/");
        return properties;
    }
}
