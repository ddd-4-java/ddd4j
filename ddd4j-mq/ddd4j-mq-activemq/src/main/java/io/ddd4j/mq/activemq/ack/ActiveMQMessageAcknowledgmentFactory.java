package io.ddd4j.mq.activemq.ack;

import io.ddd4j.mq.contract.MQMessage;
import jakarta.jms.Message;
import jakarta.jms.Session;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 从纯 Java {@link MQMessage} 头信息构建 {@link ActiveMQMessageAcknowledgment}。
 *
 * <p>2.0.x 重构：彻底移除对 {@code org.springframework.messaging.Message} 的依赖，
 * 直接基于 ddd4j-mq-core 的纯 Java {@link MQMessage} 工作。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class ActiveMQMessageAcknowledgmentFactory {

    private ActiveMQMessageAcknowledgmentFactory() {
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<ActiveMQMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Map<String, Object> headers = message.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }

        Object messageHeader = headers.get(ActiveMQMessageAcknowledgment.HEADER_JMS_MESSAGE);
        Object sessionHeader = headers.get(ActiveMQMessageAcknowledgment.HEADER_JMS_SESSION);

        Message jmsMessage = null;
        if (messageHeader instanceof Message headerMessage) {
            jmsMessage = headerMessage;
        } else if (message.getPayload() instanceof Message payloadMessage) {
            jmsMessage = payloadMessage;
        } else if (message.getNativeMessage() instanceof Message nativeMessage) {
            jmsMessage = nativeMessage;
        }
        if (jmsMessage == null) {
            return Optional.empty();
        }
        Session session = sessionHeader instanceof Session jmsSession ? jmsSession : null;
        return Optional.of(new ActiveMQMessageAcknowledgment(jmsMessage, session));
    }
}
