package io.ddd4j.mq.rabbit.acknowledgment;

import com.rabbitmq.client.Channel;
import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.acknowledgment.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 RabbitMQ {@link Channel} 的消息确认实现。
 */
@Slf4j
public final class AmqpMessageAcknowledgment implements MessageAcknowledgment {

    private final Channel channel;
    private final long deliveryTag;
    private final String messageId;
    private final String correlationId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 AMQP 确认对象。
     *
     * @param channel         Rabbit 通道
     * @param deliveryTag     投递标签
     * @param messageId       消息 ID
     * @param correlationId   关联 ID
     */
    public AmqpMessageAcknowledgment(Channel channel, long deliveryTag, String messageId, String correlationId) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.deliveryTag = deliveryTag;
        this.messageId = messageId;
        this.correlationId = correlationId;
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
        return channel.isOpen();
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.RABBIT;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        try {
            // 逻辑块：单条或批量确认成功消费
            channel.basicAck(deliveryTag, multiple);
            acknowledged.set(true);
        } catch (IOException ex) {
            throw new IllegalStateException("RabbitMQ basicAck failed, deliveryTag=" + deliveryTag, ex);
        }
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        try {
            // 逻辑块：否定确认，可控制是否重新入队
            channel.basicNack(deliveryTag, multiple, requeue);
            acknowledged.set(true);
        } catch (IOException ex) {
            throw new IllegalStateException("RabbitMQ basicNack failed, deliveryTag=" + deliveryTag, ex);
        }
    }

    @Override
    public void reject(boolean requeue) {
        ensureNotAcknowledged();
        try {
            // 逻辑块：拒绝单条消息
            channel.basicReject(deliveryTag, requeue);
            acknowledged.set(true);
        } catch (IOException ex) {
            throw new IllegalStateException("RabbitMQ basicReject failed, deliveryTag=" + deliveryTag, ex);
        }
    }

    @Override
    public void recover(boolean requeue) {
        ensureNotAcknowledged();
        try {
            // 逻辑块：通过 basicRecover 将消息重新投递
            channel.basicRecover(requeue);
            acknowledged.set(true);
        } catch (IOException ex) {
            throw new IllegalStateException("RabbitMQ basicRecover failed, deliveryTag=" + deliveryTag, ex);
        }
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (Channel.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(channel));
        }
        if (AmqpMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回底层 Rabbit 通道。
     */
    public Channel channel() {
        return channel;
    }

    /**
     * 防止重复确认导致通道异常。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new UnsupportedAckOperationException("Message already acknowledged, deliveryTag=" + deliveryTag);
        }
        if (!channel.isOpen()) {
            throw new IllegalStateException("RabbitMQ channel is closed, deliveryTag=" + deliveryTag);
        }
    }
}
