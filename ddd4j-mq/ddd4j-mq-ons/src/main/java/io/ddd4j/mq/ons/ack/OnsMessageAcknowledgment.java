package io.ddd4j.mq.ons.ack;

import com.aliyun.openservices.ons.api.Action;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 基于阿里云 ONS 消费回调语义的消息确认实现（映射 RocketMQ Action）。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class OnsMessageAcknowledgment implements MessageAcknowledgment {

    private final String messageId;
    private final String correlationId;
    private final long deliveryTag;
    private final Supplier<Action> commitAction;
    private final Supplier<Action> reconsumeAction;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 ONS 确认对象。
     *
     * @param messageId        消息 ID
     * @param correlationId    关联 ID
     * @param deliveryTag      投递序号（通常为 reconsumeTimes）
     * @param commitAction     提交成功回调
     * @param reconsumeAction  重新消费回调
     */
    public OnsMessageAcknowledgment(String messageId,
                                    String correlationId,
                                    long deliveryTag,
                                    Supplier<Action> commitAction,
                                    Supplier<Action> reconsumeAction) {
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.deliveryTag = deliveryTag;
        this.commitAction = Objects.requireNonNull(commitAction, "commitAction");
        this.reconsumeAction = Objects.requireNonNull(reconsumeAction, "reconsumeAction");
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
        return MQBrokerType.ONS;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("ONS ignores multiple ack flag, committing messageId={}", messageId);
        }
        // 逻辑块：映射为 CommitMessage
        commitAction.get();
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
            log.debug("ONS ignores multiple nack flag, messageId={}", messageId);
        }
        if (!requeue) {
            throw new UnsupportedAckOperationException(
                    "ONS does not support discard without requeue; configure dead-letter or use commit");
        }
        // 逻辑块：映射为 ReconsumeLater
        reconsumeAction.get();
        acknowledged.set(true);
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        if (requeue) {
            nack(true);
            return;
        }
        throw new UnsupportedAckOperationException("ONS does not support basicRecover without requeue");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (Action.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(acknowledged.get() ? Action.CommitMessage : Action.ReconsumeLater));
        }
        if (OnsMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 防止重复确认。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new IllegalStateException("ONS message already acknowledged, messageId=" + messageId);
        }
    }
}
