package io.ddd4j.mq.tdmq.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.contract.MQMessage;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * TDMQ 确认对象工厂。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class TdmqMessageAcknowledgmentFactory {

    public static final String HEADER_ACK_CALLBACK = "tdmq.ackCallback";
    public static final String HEADER_DELIVERY_TAG = "tdmq.deliveryTag";

    private TdmqMessageAcknowledgmentFactory() {
    }

    public static MessageAcknowledgment from(MQMessage<?> message) {
        if (Objects.isNull(message)) {
            return TdmqMessageAcknowledgment.noOp();
        }
        Object callback = message.header(HEADER_ACK_CALLBACK);
        if (callback instanceof Consumer<?> rawCallback) {
            @SuppressWarnings("unchecked")
            Consumer<Boolean> ackCallback = (Consumer<Boolean>) rawCallback;
            return new TdmqMessageAcknowledgment(
                    message.messageId(),
                    message.correlationId(),
                    deliveryTag(message),
                    ackCallback);
        }
        return TdmqMessageAcknowledgment.noOp();
    }

    private static long deliveryTag(MQMessage<?> message) {
        Object deliveryTag = message.header(HEADER_DELIVERY_TAG);
        if (deliveryTag instanceof Number n) {
            return n.longValue();
        }
        return Objects.nonNull(message.correlationId()) ? message.correlationId().hashCode() : 0L;
    }
}
