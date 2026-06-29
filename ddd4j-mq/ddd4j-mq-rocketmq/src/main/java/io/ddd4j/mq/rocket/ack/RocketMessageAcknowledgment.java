package io.ddd4j.mq.rocket.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 基于 RocketMQ {@link MessageExt} 的消息确认实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class RocketMessageAcknowledgment implements MessageAcknowledgment {

    /**
     * MQMessage headers 中存放 MessageExt 的键
     */
    public static final String HEADER_ROCKET_MESSAGE = "rocket.messageExt";

    /**
     * MQMessage headers 中存放消费结果回调的键
     */
    public static final String HEADER_ROCKET_ACK_CALLBACK = "rocket.ackCallback";

    private final MessageExt messageExt;
    private final Consumer<Boolean> ackCallback;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 RocketMQ 确认对象。
     *
     * @param messageExt  RocketMQ 消息扩展
     * @param ackCallback 消费结果回调（true=成功，false=重新消费）
     */
    public RocketMessageAcknowledgment(MessageExt messageExt, Consumer<Boolean> ackCallback) {
        this.messageExt = Objects.requireNonNull(messageExt, "messageExt");
        this.ackCallback = ackCallback;
    }

    @Override
    public long deliveryTag() {
        return messageExt.getQueueOffset();
    }

    @Override
    public String messageId() {
        return messageExt.getMsgId();
    }

    @Override
    public String correlationId() {
        return messageExt.getKeys();
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
        return MQBrokerType.ROCKET;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("RocketMQ ignores multiple ack semantics, acknowledging single message, msgId={}",
                    messageExt.getMsgId());
        }
        invokeCallback(true);
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
            log.debug("RocketMQ ignores multiple nack semantics, msgId={}", messageExt.getMsgId());
        }
        if (requeue) {
            invokeCallback(false);
        } else {
            invokeCallback(true);
        }
        acknowledged.set(true);
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedAckOperationException(MQBrokerType.ROCKET, "recover");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (MessageExt.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(messageExt));
        }
        if (RocketMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回底层 RocketMQ 消息扩展对象。
     */
    public MessageExt messageExt() {
        return messageExt;
    }

    /**
     * 防止重复确认。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new UnsupportedAckOperationException("Message already acknowledged, msgId=" + messageExt.getMsgId());
        }
    }

    /**
     * 调用消费结果回调（若存在）。
     */
    private void invokeCallback(boolean success) {
        if (ackCallback != null) {
            ackCallback.accept(success);
        }
    }
}
