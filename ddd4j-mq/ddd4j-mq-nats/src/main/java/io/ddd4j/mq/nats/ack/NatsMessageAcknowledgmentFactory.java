package io.ddd4j.mq.nats.ack;

import io.ddd4j.mq.contract.MQMessage;
import io.nats.client.Message;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 NATS {@link Message} 构建 {@link NatsMessageAcknowledgment}。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class NatsMessageAcknowledgmentFactory {

    private NatsMessageAcknowledgmentFactory() {
    }

    /**
     * 从 NATS 原生消息解析确认对象。
     *
     * @param message NATS 消息
     * @return 确认对象
     */
    public static Optional<NatsMessageAcknowledgment> fromNatsMessage(Message message) {
        if (message == null) {
            return Optional.empty();
        }
        return Optional.of(new NatsMessageAcknowledgment(message));
    }

    /**
     * 从 {@link MQMessage} 解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<NatsMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Message natsMessage = message.nativeMessage(Message.class);
        if (natsMessage != null) {
            return fromNatsMessage(natsMessage);
        }
        Object payload = message.getPayload();
        if (payload instanceof Message nativeMessage) {
            return fromNatsMessage(nativeMessage);
        }
        return Optional.empty();
    }
}
