package io.ddd4j.mq.disruptor;

import com.lmax.disruptor.RingBuffer;
import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Disruptor 本地消息确认实现：ack 为消费完成；requeue 重新发布到 RingBuffer。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DisruptorAcknowledgment implements Acknowledgment {

    private final DisruptorMQClient.Event event;
    private final RingBuffer<DisruptorMQClient.Event> ringBuffer;
    private final long deliveryTag;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * @param event       当前事件
     * @param ringBuffer  RingBuffer（requeue 用）
     * @param deliveryTag 投递序号
     */
    public DisruptorAcknowledgment(
            DisruptorMQClient.Event event,
            RingBuffer<DisruptorMQClient.Event> ringBuffer,
            long deliveryTag) {
        this.event = event;
        this.ringBuffer = ringBuffer;
        this.deliveryTag = deliveryTag;
    }

    @Override
    public long deliveryTag() {
        return deliveryTag;
    }

    @Override
    public String messageId() {
        return event.messageId;
    }

    @Override
    public String correlationId() {
        return Objects.nonNull(event.messageId) ? event.messageId : null;
    }

    @Override
    public boolean isOpen() {
        return Objects.nonNull(ringBuffer);
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.DISRUPTOR;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        acknowledged.set(true);
    }

    @Override
    public void nack(boolean requeue) {
        if (requeue && Objects.nonNull(ringBuffer) && !acknowledged.get()) {
            republish();
        }
        acknowledged.set(true);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        nack(requeue);
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        if (!requeue) {
            throw new UnsupportedOperationException("Disruptor does not support recover(false)");
        }
        nack(true);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(event)) {
            return Optional.of(nativeType.cast(event));
        }
        return Optional.empty();
    }

    /**
     * 将当前事件重新发布到 RingBuffer（本地 requeue）。
     */
    private void republish() {
        DisruptorMQClient.Event src = this.event;
        ringBuffer.publishEvent((slot, sequence) -> {
            slot.topic = src.topic;
            slot.tag = src.tag;
            slot.namespace = src.namespace;
            slot.messageId = src.messageId;
            slot.payload = src.payload;
            slot.sequence = sequence;
        });
    }
}
