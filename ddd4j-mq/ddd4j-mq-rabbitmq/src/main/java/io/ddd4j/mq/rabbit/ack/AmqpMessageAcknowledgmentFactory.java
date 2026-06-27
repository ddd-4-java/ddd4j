package io.ddd4j.mq.rabbit.ack;

import com.rabbitmq.client.Channel;
import io.ddd4j.mq.contract.MQMessage;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 Spring AMQP {@link Message} 头信息构建 {@link AmqpMessageAcknowledgment}。
 */
public final class AmqpMessageAcknowledgmentFactory {

    private AmqpMessageAcknowledgmentFactory() {
    }

    /**
     * 根据 Spring Message headers 解析确认对象。
     *
     * @param message Spring 消息
     * @return 确认对象；缺少必要头时返回 empty
     */
    public static Optional<AmqpMessageAcknowledgment> fromSpringMessage(Message<?> message) {
        Objects.requireNonNull(message, "message");
        MessageHeaders headers = message.getHeaders();

        // 逻辑块：从 AMQP 标准头提取 Channel 与 deliveryTag
        Object channelHeader = headers.get(AmqpHeaders.CHANNEL);
        Object deliveryTagHeader = headers.get(AmqpHeaders.DELIVERY_TAG);
        if (!(channelHeader instanceof Channel channel) || deliveryTagHeader == null) {
            return Optional.empty();
        }

        long deliveryTag = toLong(deliveryTagHeader);
        String messageId = headerAsString(headers, AmqpHeaders.MESSAGE_ID);
        String correlationId = headerAsString(headers, AmqpHeaders.CORRELATION_ID);
        return Optional.of(new AmqpMessageAcknowledgment(channel, deliveryTag, messageId, correlationId));
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<AmqpMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Object channelHeader = message.getHeaders().get(AmqpHeaders.CHANNEL);
        Object deliveryTagHeader = message.getHeaders().get(AmqpHeaders.DELIVERY_TAG);
        if (!(channelHeader instanceof Channel channel) || deliveryTagHeader == null) {
            return Optional.empty();
        }
        long deliveryTag = toLong(deliveryTagHeader);
        Object messageIdHeader = message.getHeaders().get(AmqpHeaders.MESSAGE_ID);
        Object correlationIdHeader = message.getHeaders().get(AmqpHeaders.CORRELATION_ID);
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

    /**
     * 读取字符串类型的 header。
     */
    private static String headerAsString(MessageHeaders headers, String key) {
        Object value = headers.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
