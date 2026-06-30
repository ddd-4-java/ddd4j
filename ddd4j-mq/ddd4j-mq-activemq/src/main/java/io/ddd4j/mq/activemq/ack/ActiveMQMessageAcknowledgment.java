package io.ddd4j.mq.activemq.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ActiveMQ Classic (JMS) manual acknowledgment mapping.
 *
 * <p>Wraps a {@link Session} and the JMS message; the
 * {@link #ack()} / {@link #nack(boolean)} methods map onto
 * {@code Session.recover()} and JMS {@code Message.acknowledge()}.
 */
public class ActiveMQMessageAcknowledgment implements MessageAcknowledgment {

    public static final String HEADER_AMQ_SESSION = "ddd4j.activemq.session";
    public static final String HEADER_AMQ_MESSAGE = "ddd4j.activemq.message";
    public static final String HEADER_AMQ_DELIVERY_ID = "ddd4j.activemq.deliveryId";

    private final Session session;
    private final Message message;
    private final long deliveryId;
    private final String messageId;
    private final String correlationId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    public ActiveMQMessageAcknowledgment(Session session, Message message,
                                         long deliveryId, String messageId, String correlationId) {
        this.session = session;
        this.message = message;
        this.deliveryId = deliveryId;
        this.messageId = messageId;
        this.correlationId = correlationId;
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
        return java.util.Objects.nonNull(session) && !acknowledged.get();
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ACTIVEMQ;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        runOnce(() -> message.acknowledge());
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        // JMS 没有 native 的 nack(requeue)，通过 Session.recover() 把已消费但未 ack 的消息重投
        if (requeue) {
            runOnce(() -> session.recover());
        } else {
            // requeue=false 等价于 ack（不重新入队，通常进 DLQ）
            runOnce(() -> message.acknowledge());
        }
    }

    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void recover(boolean requeue) {
        runOnce(() -> session.recover());
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (java.util.Objects.isNull(nativeType)) {
            return Optional.empty();
        }
        if (nativeType.isInstance(session)) {
            return Optional.of(nativeType.cast(session));
        }
        if (nativeType.isInstance(message)) {
            return Optional.of(nativeType.cast(message));
        }
        if (nativeType.isInstance(this)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedAckOperationException(
                    "ActiveMQ message already acknowledged, deliveryId=" + deliveryId);
        }
        try {
            op.run();
        } catch (JMSException ex) {
            acknowledged.set(false);
            throw new IllegalStateException(
                    "ActiveMQ acknowledgment failed, deliveryId=" + deliveryId, ex);
        }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws JMSException;
    }
}
