package io.ddd4j.boot.cmpt.disruptor.acknowledgment;

import io.ddd4j.boot.cmpt.disruptor.core.DisruptorMQEvent;
import io.ddd4j.boot.mq.contract.MQMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 {@link MQMessage} 解析 {@link DisruptorMessageAcknowledgment}。
 */
public final class DisruptorMessageAcknowledgmentFactory {

    private DisruptorMessageAcknowledgmentFactory() {
    }

    /**
     * 从 MQ 信封解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<DisruptorMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        DisruptorMQEvent event = message.nativeMessage(DisruptorMQEvent.class);
        if (event == null) {
            return Optional.empty();
        }
        Object tagHeader = message.headers().get("disruptor.deliveryTag");
        long deliveryTag = tagHeader instanceof Number number ? number.longValue() : event.getSequence();
        return Optional.of(new DisruptorMessageAcknowledgment(event, null, deliveryTag));
    }
}
