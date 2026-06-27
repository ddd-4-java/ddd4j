package io.ddd4j.mq.rocket.ack;

import io.ddd4j.mq.contract.MQMessage;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 从 Spring RocketMQ {@link Message} 构建 {@link RocketMessageAcknowledgment}。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class RocketMessageAcknowledgmentFactory {

    private RocketMessageAcknowledgmentFactory() {
    }

    /**
     * 根据 Spring Message headers 解析确认对象。
     *
     * @param message Spring 消息
     * @return 确认对象；缺少必要头时返回 empty
     */
    public static Optional<RocketMessageAcknowledgment> fromSpringMessage(Message<?> message) {
        Objects.requireNonNull(message, "message");
        MessageExt messageExt = resolveMessageExt(message.getHeaders(), message);
        if (messageExt == null) {
            return Optional.empty();
        }
        Consumer<Boolean> ackCallback = message.getHeaders().get(
                RocketMessageAcknowledgment.HEADER_ROCKET_ACK_CALLBACK, Consumer.class);
        return Optional.of(new RocketMessageAcknowledgment(messageExt, ackCallback));
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    @SuppressWarnings("unchecked")
    public static Optional<RocketMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Object messageExtHeader = message.getHeaders().get(RocketMessageAcknowledgment.HEADER_ROCKET_MESSAGE);
        MessageExt messageExt;
        if (messageExtHeader instanceof MessageExt ext) {
            messageExt = ext;
        } else {
            Object nativePayload = message.getPayload();
            if (nativePayload instanceof MessageExt nativeExt) {
                messageExt = nativeExt;
            } else {
                return Optional.empty();
            }
        }
        Object callbackHeader = message.getHeaders().get(RocketMessageAcknowledgment.HEADER_ROCKET_ACK_CALLBACK);
        Consumer<Boolean> ackCallback = callbackHeader instanceof Consumer<?> consumer
                ? (Consumer<Boolean>) consumer
                : null;
        return Optional.of(new RocketMessageAcknowledgment(messageExt, ackCallback));
    }

    /**
     * 从 headers 或 payload 解析 MessageExt。
     */
    private static MessageExt resolveMessageExt(MessageHeaders headers, Message<?> message) {
        Object ext = headers.get(RocketMessageAcknowledgment.HEADER_ROCKET_MESSAGE);
        if (ext instanceof MessageExt messageExt) {
            return messageExt;
        }
        Object payload = message.getPayload();
        if (payload instanceof MessageExt messageExt) {
            return messageExt;
        }
        return null;
    }
}
