package io.ddd4j.mq.nats.ack;

import io.ddd4j.mq.message.Message;
import io.nats.client.Message;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 NATS {@link Message} 构建 {@link NatsAcknowledgment}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class NatsAcknowledgmentFactory {

    private NatsAcknowledgmentFactory() {
    }

    /**
     * 从 NATS 原生消息解析确认对象。
     *
     * @param message NATS 消息
     * @return 确认对象
     */
    public static Optional<NatsAcknowledgment> fromNatsMessage(Message message) {
        if (Objects.isNull(message)) {
            return Optional.empty();
        }
        return Optional.of(new NatsAcknowledgment(message));
    }

    /**
     * 从 {@link Message} 解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<NatsAcknowledgment> from(Message<?> message) {
        Objects.requireNonNull(message, "message");
        Message natsMessage = message.nativeMessage(Message.class);
        if (Objects.nonNull(natsMessage)) {
            return fromNatsMessage(natsMessage);
        }
        Object payload = message.getPayload();
        if (payload instanceof Message nativeMessage) {
            return fromNatsMessage(nativeMessage);
        }
        return Optional.empty();
    }
}
