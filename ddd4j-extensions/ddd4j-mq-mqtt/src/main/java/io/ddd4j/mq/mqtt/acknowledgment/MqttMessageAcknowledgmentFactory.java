package io.ddd4j.mq.mqtt.acknowledgment;

import io.ddd4j.mq.acknowledgment.NoOpMessageAcknowledgment;
import io.ddd4j.mq.contract.MQMessage;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 Spring Integration MQTT {@link Message} 头信息构建 {@link MqttMessageAcknowledgment}。
 */
public final class MqttMessageAcknowledgmentFactory {

    private MqttMessageAcknowledgmentFactory() {
    }

    /**
     * 根据 Spring Message headers 解析确认对象。
     *
     * @param message Spring 消息
     * @return 确认对象；QoS 0 时返回 NoOp 包装
     */
    public static MessageAcknowledgmentOrNoOp fromSpringMessage(Message<?> message) {
        Objects.requireNonNull(message, "message");
        MessageHeaders headers = message.getHeaders();
        String topic = headerAsString(headers, MqttHeaders.RECEIVED_TOPIC);
        if (topic == null) {
            topic = headerAsString(headers, MqttHeaders.TOPIC);
        }
        int qos = resolveQos(headers);
        String messageId = headerAsString(headers, MqttHeaders.ID);

        if (qos <= 0) {
            return new MessageAcknowledgmentOrNoOp(
                    new NoOpMessageAcknowledgment(), false);
        }
        return new MessageAcknowledgmentOrNoOp(
                new MqttMessageAcknowledgment(topic, qos, messageId), true);
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<MqttMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Object topicHeader = message.headers().get(MqttHeaders.RECEIVED_TOPIC);
        if (topicHeader == null) {
            topicHeader = message.headers().get(MqttHeaders.TOPIC);
        }
        Object qosHeader = message.headers().get(MqttHeaders.RECEIVED_QOS);
        if (!(topicHeader instanceof String topic)) {
            return Optional.empty();
        }
        int qos = qosHeader instanceof Number number ? number.intValue() : 0;
        Object idHeader = message.headers().get(MqttHeaders.ID);
        String messageId = idHeader == null ? null : String.valueOf(idHeader);
        if (qos <= 0) {
            return Optional.empty();
        }
        return Optional.of(new MqttMessageAcknowledgment(topic, qos, messageId));
    }

    /**
     * 解析 QoS header。
     */
    private static int resolveQos(MessageHeaders headers) {
        Object qosHeader = headers.get(MqttHeaders.RECEIVED_QOS);
        if (qosHeader instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 读取字符串类型的 header。
     */
    private static String headerAsString(MessageHeaders headers, String key) {
        Object value = headers.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 确认对象与是否为 QoS 确认的简单包装。
     *
     * @param acknowledgment 确认实现
     * @param qosAck         是否为 QoS 级确认
     */
    public record MessageAcknowledgmentOrNoOp(
            io.ddd4j.mq.acknowledgment.MessageAcknowledgment acknowledgment,
            boolean qosAck) {
    }
}
