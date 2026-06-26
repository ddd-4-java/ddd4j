package io.ddd4j.mq.activemq.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 JMS {@link Message} 与 {@link Session} 的消息确认实现。
 */
@Slf4j
public final class ActiveMQMessageAcknowledgment implements MessageAcknowledgment {

    /** MQMessage headers 中存放 JMS Message 的键 */
    public static final String HEADER_JMS_MESSAGE = "jms.message";

    /** MQMessage headers 中存放 JMS Session 的键 */
    public static final String HEADER_JMS_SESSION = "jms.session";

    private final Message jmsMessage;
    private final Session session;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 JMS 确认对象。
     *
     * @param jmsMessage JMS 消息
     * @param session    JMS 会话
     */
    public ActiveMQMessageAcknowledgment(Message jmsMessage, Session session) {
        this.jmsMessage = Objects.requireNonNull(jmsMessage, "jmsMessage");
        this.session = session;
    }

    @Override
    public long deliveryTag() {
        try {
            return jmsMessage.getJMSDeliveryTime();
        } catch (JMSException ex) {
            return jmsMessage.hashCode();
        }
    }

    @Override
    public String messageId() {
        try {
            return jmsMessage.getJMSMessageID();
        } catch (JMSException ex) {
            return null;
        }
    }

    @Override
    public String correlationId() {
        try {
            return jmsMessage.getJMSCorrelationID();
        } catch (JMSException ex) {
            return null;
        }
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
        return MQBrokerType.ACTIVEMQ;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            throw new UnsupportedAckOperationException(MQBrokerType.ACTIVEMQ, "ack(multiple=true)");
        }
        try {
            // 逻辑块：CLIENT_ACKNOWLEDGE 模式下确认单条消息
            jmsMessage.acknowledge();
            acknowledged.set(true);
        } catch (JMSException ex) {
            throw new IllegalStateException("JMS acknowledge failed, messageId=" + messageId(), ex);
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
            throw new UnsupportedAckOperationException(MQBrokerType.ACTIVEMQ, "nack(multiple=true)");
        }
        if (requeue && session != null) {
            try {
                // 逻辑块：recover 使消息重新投递
                session.recover();
                acknowledged.set(true);
            } catch (JMSException ex) {
                throw new IllegalStateException("JMS session.recover failed, messageId=" + messageId(), ex);
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
        ensureNotAcknowledged();
        if (session == null) {
            throw new UnsupportedAckOperationException(MQBrokerType.ACTIVEMQ, "recover without session");
        }
        try {
            // 逻辑块：session.recover 恢复未确认消息
            session.recover();
            acknowledged.set(true);
        } catch (JMSException ex) {
            throw new IllegalStateException("JMS recover failed, messageId=" + messageId(), ex);
        }
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (Message.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(jmsMessage));
        }
        if (Session.class.isAssignableFrom(nativeType)) {
            return session == null ? Optional.empty() : Optional.of(nativeType.cast(session));
        }
        if (ActiveMQMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回底层 JMS 消息。
     */
    public Message jmsMessage() {
        return jmsMessage;
    }

    /**
     * 返回底层 JMS 会话。
     */
    public Session session() {
        return session;
    }

    /**
     * 防止重复确认。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new UnsupportedAckOperationException("Message already acknowledged, messageId=" + messageId());
        }
    }
}
