package io.ddd4j.mq.activemq.acknowledgment;

import io.ddd4j.mq.contract.MQMessage;
import jakarta.jms.Message;
import jakarta.jms.Session;
import org.springframework.messaging.MessageHeaders;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 Spring JMS {@link org.springframework.messaging.Message} 构建 {@link ActiveMQMessageAcknowledgment}。
 */
public final class ActiveMQMessageAcknowledgmentFactory {

    private ActiveMQMessageAcknowledgmentFactory() {
    }

    /**
     * 根据 Spring Message headers 解析确认对象。
     *
     * @param message Spring 消息
     * @return 确认对象；缺少必要头时返回 empty
     */
    public static Optional<ActiveMQMessageAcknowledgment> fromSpringMessage(
            org.springframework.messaging.Message<?> message) {
        Objects.requireNonNull(message, "message");
        MessageHeaders headers = message.getHeaders();

        Session session = headers.get(ActiveMQMessageAcknowledgment.HEADER_JMS_SESSION, Session.class);
        Message jmsMessage = headers.get(ActiveMQMessageAcknowledgment.HEADER_JMS_MESSAGE, Message.class);
        if (jmsMessage == null) {
            Object payload = message.getPayload();
            if (payload instanceof Message payloadMessage) {
                jmsMessage = payloadMessage;
            }
        }
        if (jmsMessage == null) {
            return Optional.empty();
        }
        return Optional.of(new ActiveMQMessageAcknowledgment(jmsMessage, session));
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<ActiveMQMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Object messageHeader = message.headers().get(ActiveMQMessageAcknowledgment.HEADER_JMS_MESSAGE);
        Object sessionHeader = message.headers().get(ActiveMQMessageAcknowledgment.HEADER_JMS_SESSION);

        Message jmsMessage = null;
        if (messageHeader instanceof Message headerMessage) {
            jmsMessage = headerMessage;
        } else if (message.payload() instanceof Message payloadMessage) {
            jmsMessage = payloadMessage;
        } else if (message.nativeMessage() instanceof Message nativeMessage) {
            jmsMessage = nativeMessage;
        }
        if (jmsMessage == null) {
            return Optional.empty();
        }
        Session session = sessionHeader instanceof Session jmsSession ? jmsSession : null;
        return Optional.of(new ActiveMQMessageAcknowledgment(jmsMessage, session));
    }
}
