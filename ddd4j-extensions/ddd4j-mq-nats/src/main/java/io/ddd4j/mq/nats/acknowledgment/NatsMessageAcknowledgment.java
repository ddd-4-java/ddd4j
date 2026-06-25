package io.ddd4j.mq.nats.acknowledgment;

import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.acknowledgment.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import io.nats.client.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 NATS JetStream {@link Message} 的消息确认实现。
 */
@Slf4j
public final class NatsMessageAcknowledgment implements MessageAcknowledgment {

    private final Message message;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 NATS 确认对象。
     *
     * @param message JetStream 消息
     */
    public NatsMessageAcknowledgment(Message message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    @Override
    public long deliveryTag() {
        return message.metaData() == null ? 0L : message.metaData().consumerSequence();
    }

    @Override
    public String messageId() {
        return message.getSID();
    }

    @Override
    public String correlationId() {
        return message.getReplyTo();
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
        return MQBrokerType.NATS;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("NATS ignores multiple ack flag, acknowledging sequence={}", deliveryTag());
        }
        // 逻辑块：JetStream 确认成功消费
        message.ack();
        acknowledged.set(true);
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("NATS ignores multiple nack flag, sequence={}", deliveryTag());
        }
        if (!requeue) {
            throw new UnsupportedAckOperationException(
                    "NATS JetStream nack without requeue is not supported; use ack or nak with requeue");
        }
        // 逻辑块：否定确认并重新投递
        message.nak();
        acknowledged.set(true);
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedAckOperationException("NATS does not support basicRecover semantics");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (Message.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(message));
        }
        if (NatsMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回底层 NATS 消息。
     */
    public Message message() {
        return message;
    }

    /**
     * 防止重复确认导致 JetStream 状态异常。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new IllegalStateException("NATS message already acknowledged, sequence=" + deliveryTag());
        }
    }
}
