package io.ddd4j.boot.cmpt.tdmq.acknowledgment;

import io.ddd4j.boot.mq.contract.MQMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 MQ 信封构建 {@link TdmqMessageAcknowledgment}。
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
        Object payload = message.payload();
        if (payload instanceof TdmqMessageAcknowledgment nativeAck) {
            return Optional.of(nativeAck);
        }
        return Optional.empty();
    }
}
