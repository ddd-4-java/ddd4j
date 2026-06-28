package io.ddd4j.mq.mqtt.ack;

import io.ddd4j.mq.ack.NoOpMessageAcknowledgment;
import io.ddd4j.mq.contract.MQMessage;
import org.springframework.integration.mqtt.support.MqttHeaders;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 从纯 Java {@link MQMessage} 头信息构建 {@link MqttMessageAcknowledgment}。
 *
 * <p>2.0.x 重构：彻底移除对 {@code org.springframework.messaging.Message} 的依赖，
 * 直接基于 ddd4j-mq-core 的纯 Java {@link MQMessage} 工作。
 *
 * <p>注：{@code MqttHeaders} 来自 spring-integration-mqtt，属于 MQTT 客户端设计约束。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class MqttMessageAcknowledgmentFactory {

    private MqttMessageAcknowledgmentFactory() {
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象；QoS 0 时返回 empty
     */
    public static Optional<MqttMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Map<String, Object> headers = message.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }

        Object topicHeader = headers.get(MqttHeaders.RECEIVED_TOPIC);
        if (topicHeader == null) {
            topicHeader = headers.get(MqttHeaders.TOPIC);
        }
        Object qosHeader = headers.get(MqttHeaders.RECEIVED_QOS);
        if (!(topicHeader instanceof String topic)) {
            return Optional.empty();
        }
        int qos = qosHeader instanceof Number number ? number.intValue() : 0;
        Object idHeader = headers.get(MqttHeaders.ID);
        String messageId = idHeader == null ? null : String.valueOf(idHeader);
        if (qos <= 0) {
            return Optional.empty();
        }
        return Optional.of(new MqttMessageAcknowledgment(topic, qos, messageId));
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象，QoS 0 时返回 NoOp 包装。
     *
     * @param message MQ 信封
     * @return 确认对象与是否为 QoS 确认的包装
     */
    public static MessageAcknowledgmentOrNoOp resolve(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Optional<MqttMessageAcknowledgment> ack = from(message);
        if (ack.isPresent()) {
            return new MessageAcknowledgmentOrNoOp(ack.get(), true);
        }
        return new MessageAcknowledgmentOrNoOp(new NoOpMessageAcknowledgment(), false);
    }

    /**
     * 确认对象与是否为 QoS 确认的简单包装。
     *
     * @param acknowledgment 确认实现
     * @param qosAck         是否为 QoS 级确认
     */
    public record MessageAcknowledgmentOrNoOp(
            io.ddd4j.mq.ack.MessageAcknowledgment acknowledgment,
            boolean qosAck) {
    }
}
