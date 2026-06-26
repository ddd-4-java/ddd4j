package io.ddd4j.mq.tdmq.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.NoOpMessageAcknowledgment;
import io.ddd4j.mq.ack.AcknowledgmentContext;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 腾讯云 TDMQ 消息确认实现（占位：委托 ack/nack 回调，待 SDK 接入后替换）。
 */
@Slf4j
public final class TdmqMessageAcknowledgment implements MessageAcknowledgment {

    private final String messageId;
    private final String correlationId;
    private final long deliveryTag;
    private final Consumer<Boolean> ackCallback;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 TDMQ 确认对象。
     *
     * @param messageId     消息 ID
     * @param correlationId 关联 ID
     * @param deliveryTag   投递标签
     * @param ackCallback   确认回调（true=ack, false=nack/requeue）
     */
    public TdmqMessageAcknowledgment(String messageId,
                                     String correlationId,
                                     long deliveryTag,
                                     Consumer<Boolean> ackCallback) {
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.deliveryTag = deliveryTag;
        this.ackCallback = Objects.requireNonNull(ackCallback, "ackCallback");
    }

    @Override
    public long deliveryTag() {
        return deliveryTag;
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
        return !acknowledged.get();
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.TDMQ;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        ackCallback.accept(true);
        acknowledged.set(true);
        log.debug("TDMQ ack messageId={}, multiple={}", messageId, multiple);
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        ackCallback.accept(requeue);
        acknowledged.set(true);
        log.debug("TDMQ nack messageId={}, requeue={}, multiple={}", messageId, requeue, multiple);
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        nack(requeue);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (TdmqMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 创建占位 NoOp 确认（客户端未就绪时使用）。
     */
    public static MessageAcknowledgment noOp() {
        return new NoOpMessageAcknowledgment(AcknowledgmentContext.builder()
                .brokerType(MQBrokerType.TDMQ)
                .build());
    }

    /**
     * 防止重复确认。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new IllegalStateException("TDMQ message already acknowledged, messageId=" + messageId);
        }
    }
}
