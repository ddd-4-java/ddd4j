package io.ddd4j.mq.disruptor.ack;

import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.disruptor.core.DisruptorMQEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 {@link Message} 解析 {@link DisruptorAcknowledgment}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class DisruptorAcknowledgmentFactory {

    private DisruptorAcknowledgmentFactory() {
    }

    /**
     * 从 MQ 信封解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<DisruptorAcknowledgment> from(Message<?> message) {
        Objects.requireNonNull(message, "message");
        DisruptorMQEvent event = message.nativeMessage(DisruptorMQEvent.class);
        if (Objects.isNull(event)) {
            return Optional.empty();
        }
        Object tagHeader = message.getHeaders().get("disruptor.deliveryTag");
        long deliveryTag = tagHeader instanceof Number number ? number.longValue() : event.getSequence();
        return Optional.of(new DisruptorAcknowledgment(event, null, deliveryTag));
    }
}
