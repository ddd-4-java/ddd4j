package io.ddd4j.mq.pulsar.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Apache Pulsar manual acknowledgment mapping.
 *
 * <p>Wraps a {@link Consumer} and the received {@link Message} (Pulsar Message, not JMS Message).
 * Pulsar nack semantics: {@link Consumer#negativeAcknowledge(Message)} redelivers
 * the message (with optional delay), {@link Consumer#acknowledge(Message)} acks.
 */
public class PulsarMessageAcknowledgment implements MessageAcknowledgment {

    public static final String HEADER_PULSAR_CONSUMER = "ddd4j.pulsar.consumer";
    public static final String HEADER_PULSAR_MESSAGE = "ddd4j.pulsar.message";
    public static final String HEADER_PULSAR_MESSAGE_ID = "ddd4j.pulsar.messageId";

    private final Consumer<?> consumer;
    private final Message<?> message;
    private final String messageId;
    private final String correlationId;
    private final long deliveryId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    public PulsarMessageAcknowledgment(Consumer<?> consumer, Message<?> message,
                                       String messageId, String correlationId) {
        this.consumer = consumer;
        this.message = message;
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.deliveryId = java.util.Objects.isNull(messageId) ? 0L : Math.abs((long) messageId.hashCode());
    }

    @Override
    public long deliveryTag() {
        return deliveryId;
    }

    @Override
    public String messageId() {
        return messageId;
    }

    @Override
    public String correlationId() {
        return correlationId;
    }

    @Override
    public boolean isOpen() {
        return java.util.Objects.nonNull(consumer) && consumer.isConnected();
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.PULSAR;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        runOnce(() -> {
            if (multiple) {
                consumer.acknowledgeCumulative(message.getMessageId());
            } else {
                consumer.acknowledge(message);
            }
        });
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        runOnce(() -> {
            if (requeue) {
                consumer.negativeAcknowledge(message);
            } else {
                // nack + 不重入队：Pulsar 没有原生 DLQ，需业务侧配置 broker policy；
                // 此处仍走 negativeAcknowledge，依赖 broker 的 redelivery 配置
                consumer.negativeAcknowledge(message);
            }
        });
    }

    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void recover(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (java.util.Objects.isNull(type)) {
            return Optional.empty();
        }
        if (type.isInstance(consumer)) {
            return Optional.of(type.cast(consumer));
        }
        if (type.isInstance(message)) {
            return Optional.of(type.cast(message));
        }
        if (type.isInstance(messageId())) {
            return Optional.of(type.cast(messageId()));
        }
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedAckOperationException(
                    "Pulsar message already acknowledged, id=" + messageId);
        }
        try {
            op.run();
        } catch (Exception ex) {
            acknowledged.set(false);
            throw new IllegalStateException("Pulsar ack failed, id=" + messageId, ex);
        }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws Exception;
    }
}
