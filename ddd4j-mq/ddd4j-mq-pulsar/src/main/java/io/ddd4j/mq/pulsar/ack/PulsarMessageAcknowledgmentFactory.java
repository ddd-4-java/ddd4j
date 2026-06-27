package io.ddd4j.mq.pulsar.ack;

import io.ddd4j.mq.contract.MQMessage;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 Spring Pulsar {@link org.springframework.messaging.Message} 构建 {@link PulsarMessageAcknowledgment}。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class PulsarMessageAcknowledgmentFactory {

    private PulsarMessageAcknowledgmentFactory() {
    }

    /**
     * 根据 Spring Message headers 解析确认对象。
     *
     * @param message Spring 消息
     * @return 确认对象；缺少必要头时返回 empty
     */
    public static Optional<PulsarMessageAcknowledgment> fromSpringMessage(
            org.springframework.messaging.Message<?> message) {
        Objects.requireNonNull(message, "message");
        MessageHeaders headers = message.getHeaders();

        // 逻辑块：从自定义 Pulsar 头提取 Consumer 与 Message
        Consumer<?> consumer = headers.get(PulsarMessageAcknowledgment.HEADER_PULSAR_CONSUMER, Consumer.class);
        Message<?> pulsarMessage = headers.get(PulsarMessageAcknowledgment.HEADER_PULSAR_MESSAGE, Message.class);
        if (consumer == null || pulsarMessage == null) {
            Object payload = message.getPayload();
            if (payload instanceof Message<?> payloadMessage) {
                pulsarMessage = payloadMessage;
            }
        }
        if (consumer == null || pulsarMessage == null) {
            return Optional.empty();
        }
        return Optional.of(new PulsarMessageAcknowledgment(consumer, pulsarMessage));
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<PulsarMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Object consumerHeader = message.getHeaders().get(PulsarMessageAcknowledgment.HEADER_PULSAR_CONSUMER);
        Object messageHeader = message.getHeaders().get(PulsarMessageAcknowledgment.HEADER_PULSAR_MESSAGE);
        if (!(consumerHeader instanceof Consumer<?> consumer) || !(messageHeader instanceof Message<?> pulsarMessage)) {
            return Optional.empty();
        }
        return Optional.of(new PulsarMessageAcknowledgment(consumer, pulsarMessage));
    }
}
