package io.ddd4j.mq.ons.ack;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.Message;
import io.ddd4j.mq.contract.MQMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 ONS {@link Message} 构建 {@link OnsMessageAcknowledgment}。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class OnsMessageAcknowledgmentFactory {

    private OnsMessageAcknowledgmentFactory() {
    }

    /**
     * 从 ONS 原生消息解析确认对象（骨架：需配合消费上下文回调）。
     *
     * @param message ONS 消息
     * @return 确认对象
     */
    public static Optional<OnsMessageAcknowledgment> fromOnsMessage(Message message) {
        if (message == null) {
            return Optional.empty();
        }
        return Optional.of(new OnsMessageAcknowledgment(
                message.getMsgID(),
                message.getKey(),
                message.getReconsumeTimes(),
                () -> Action.CommitMessage,
                () -> Action.ReconsumeLater));
    }

    /**
     * 从 {@link MQMessage} 解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<OnsMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Message onsMessage = message.nativeMessage(Message.class);
        if (onsMessage != null) {
            return fromOnsMessage(onsMessage);
        }
        Object payload = message.getPayload();
        if (payload instanceof Message nativeMessage) {
            return fromOnsMessage(nativeMessage);
        }
        OnsMessageAcknowledgment ack = message.nativeMessage(OnsMessageAcknowledgment.class);
        return ack == null ? Optional.empty() : Optional.of(ack);
    }
}
