package io.ddd4j.mq.rabbit.ack;

import com.rabbitmq.client.Channel;
import io.ddd4j.mq.contract.MQMessage;
import org.springframework.amqp.support.AmqpHeaders;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 从纯 Java {@link MQMessage} 头信息构建 {@link AmqpMessageAcknowledgment}。
 *
 * <p>2.0.x 重构：彻底移除对 {@code org.springframework.messaging.Message} 的类型依赖，
 * 直接基于 ddd4j-mq-core 定义的纯 Java {@link MQMessage} 工作。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class AmqpMessageAcknowledgmentFactory {

    private AmqpMessageAcknowledgmentFactory() {
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message 纯 Java MQ 信封
     * @return 确认对象；缺少必要头时返回 empty
     */
    public static Optional<AmqpMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Map<String, Object> headers = message.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }

        // 逻辑块：从 AMQP 标准头提取 Channel 与 deliveryTag
        Object channelHeader = headers.get(AmqpHeaders.CHANNEL);
        Object deliveryTagHeader = headers.get(AmqpHeaders.DELIVERY_TAG);
        if (!(channelHeader instanceof Channel channel) || deliveryTagHeader == null) {
            return Optional.empty();
        }

        long deliveryTag = toLong(deliveryTagHeader);
        Object messageIdHeader = headers.get(AmqpHeaders.MESSAGE_ID);
        Object correlationIdHeader = headers.get(AmqpHeaders.CORRELATION_ID);
        String messageId = messageIdHeader == null ? null : String.valueOf(messageIdHeader);
        String correlationId = correlationIdHeader == null ? null : String.valueOf(correlationIdHeader);
        return Optional.of(new AmqpMessageAcknowledgment(channel, deliveryTag, messageId, correlationId));
    }

    /**
     * 将 header 值安全转换为 long。
     */
    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
