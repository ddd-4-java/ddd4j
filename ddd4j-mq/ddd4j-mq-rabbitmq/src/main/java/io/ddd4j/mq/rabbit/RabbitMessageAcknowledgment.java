package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.Channel;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RabbitMQ manual acknowledgment mapping.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RabbitMessageAcknowledgment implements MessageAcknowledgment {

    public static final String HEADER_RABBIT_CHANNEL = "ddd4j.rabbit.channel";
    public static final String HEADER_RABBIT_DELIVERY_TAG = "ddd4j.rabbit.deliveryTag";

    private final Channel channel;
    private final long deliveryTag;
    private final String messageId;
    private final String correlationId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    public RabbitMessageAcknowledgment(Channel channel, long deliveryTag, String messageId, String correlationId) {
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
        runOnce(() -> channel.basicAck(deliveryTag, multiple));
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        runOnce(() -> channel.basicNack(deliveryTag, multiple, requeue));
    }

    @Override
    public void reject(boolean requeue) {
        runOnce(() -> channel.basicReject(deliveryTag, requeue));
    }

    @Override
    public void recover(boolean requeue) {
        runOnce(() -> channel.basicRecover(requeue));
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (java.util.Objects.isNull(nativeType)) {
            return Optional.empty();
        }
        if (nativeType.isInstance(channel)) {
            return Optional.of(nativeType.cast(channel));
        }
        if (nativeType.isInstance(this)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    private void runOnce(IoOperation operation) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedAckOperationException("Message already acknowledged, deliveryTag=" + deliveryTag);
        }
        try {
            operation.run();
        } catch (IOException ex) {
            acknowledged.set(false);
            throw new IllegalStateException("RabbitMQ acknowledgment operation failed, deliveryTag=" + deliveryTag, ex);
        }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws IOException;
    }
}
