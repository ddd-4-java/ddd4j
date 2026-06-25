package io.ddd4j.mq.pulsar.acknowledgment;

import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.acknowledgment.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 Pulsar {@link Consumer} 与 {@link MessageId} 的消息确认实现。
 */
@Slf4j
public final class PulsarMessageAcknowledgment implements MessageAcknowledgment {

    /** MQMessage headers 中存放 Pulsar Consumer 的键 */
    public static final String HEADER_PULSAR_CONSUMER = "pulsar.consumer";

    /** MQMessage headers 中存放 Pulsar Message 的键 */
    public static final String HEADER_PULSAR_MESSAGE = "pulsar.message";

    private final Consumer<?> consumer;
    private final Message<?> pulsarMessage;
    private final MessageId messageId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 Pulsar 确认对象。
     *
     * @param consumer      Pulsar 消费者
     * @param pulsarMessage Pulsar 消息
     */
    public PulsarMessageAcknowledgment(Consumer<?> consumer, Message<?> pulsarMessage) {
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.pulsarMessage = Objects.requireNonNull(pulsarMessage, "pulsarMessage");
        this.messageId = pulsarMessage.getMessageId();
    }

    @Override
    public long deliveryTag() {
        return messageId.hashCode();
    }

    @Override
    public String messageId() {
        return messageId.toString();
    }

    @Override
    public String correlationId() {
        return pulsarMessage.getProperty("correlationId");
    }

    @Override
    public boolean isOpen() {
        return !acknowledged.get();
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
        ensureNotAcknowledged();
        if (multiple) {
            throw new UnsupportedAckOperationException(MQBrokerType.PULSAR, "ack(multiple=true)");
        }
        try {
            // 逻辑块：单条确认成功消费
            consumer.acknowledge(pulsarMessage);
            acknowledged.set(true);
        } catch (Exception ex) {
            throw new IllegalStateException("Pulsar acknowledge failed, messageId=" + messageId, ex);
        }
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        if (multiple) {
            throw new UnsupportedAckOperationException(MQBrokerType.PULSAR, "nack(multiple=true)");
        }
        if (requeue) {
            try {
                // 逻辑块：negativeAcknowledge 触发重新投递
                consumer.negativeAcknowledge(pulsarMessage);
                acknowledged.set(true);
            } catch (Exception ex) {
                throw new IllegalStateException("Pulsar negativeAcknowledge failed, messageId=" + messageId, ex);
            }
        } else {
            ack(false);
        }
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedAckOperationException(MQBrokerType.PULSAR, "recover");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (Consumer.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(consumer));
        }
        if (Message.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(pulsarMessage));
        }
        if (MessageId.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(messageId));
        }
        if (PulsarMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回底层 Pulsar 消费者。
     */
    public Consumer<?> consumer() {
        return consumer;
    }

    /**
     * 返回底层 Pulsar 消息。
     */
    public Message<?> pulsarMessage() {
        return pulsarMessage;
    }

    /**
     * 防止重复确认。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new UnsupportedAckOperationException("Message already acknowledged, messageId=" + messageId);
        }
    }
}
