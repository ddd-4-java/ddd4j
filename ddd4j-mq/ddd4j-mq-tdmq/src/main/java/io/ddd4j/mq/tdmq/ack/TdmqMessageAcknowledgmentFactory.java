package io.ddd4j.mq.tdmq.ack;

import io.ddd4j.mq.contract.MQMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 MQ 信封构建 {@link TdmqMessageAcknowledgment}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class TdmqMessageAcknowledgmentFactory {

    private TdmqMessageAcknowledgmentFactory() {
    }

    /**
     * 从 {@link MQMessage} 解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<TdmqMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        TdmqMessageAcknowledgment ack = message.nativeMessage(TdmqMessageAcknowledgment.class);
        if (ack != null) {
            return Optional.of(ack);
        }
        Object payload = message.getPayload();
        if (payload instanceof TdmqMessageAcknowledgment nativeAck) {
            return Optional.of(nativeAck);
        }
        return Optional.empty();
    }
}
