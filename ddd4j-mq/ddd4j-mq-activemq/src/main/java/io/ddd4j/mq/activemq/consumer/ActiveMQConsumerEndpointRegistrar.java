package io.ddd4j.mq.activemq.consumer;

import io.ddd4j.mq.activemq.ack.ActiveMQMessageAcknowledgment;
import io.ddd4j.mq.activemq.config.ActiveMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQTagMatcher;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Programmatic ActiveMQ (JMS) consumer registrar.
 *
 * <p>Uses native JMS {@link MessageListener} (no Spring listener container).
 */
public class ActiveMQConsumerEndpointRegistrar {

    private final Connection connection;
    private final ActiveMQProperties properties;

    public ActiveMQConsumerEndpointRegistrar(Connection connection, ActiveMQProperties properties) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        try {
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Destination destination = resolveDestination(session, definition);
            if (properties.isAutoDeclare()) {
                // ActiveMQ 会按需自动创建 destination（默认行为），无需显式 declare
            }
            MessageConsumer consumer = session.createConsumer(destination);
            consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    handleMessage(session, message, definition, handler);
                }
            });
        } catch (JMSException ex) {
            throw new IllegalStateException("Register ActiveMQ consumer failed", ex);
        }
    }

    private void handleMessage(Session session, Message message, MQListenerDefinition def, MQConsumerHandler handler) {
        try {
            String tag = extractTag(message, def);
            if (!MQTagMatcher.match(tag, def.getTags())) {
                try { message.acknowledge(); } catch (JMSException ignore) {}
                return;
            }
            MQMessage<String> mqMessage = toMessage(message, session);
            ActiveMQMessageAcknowledgment ack = new ActiveMQMessageAcknowledgment(
                    session, message, messageIdHash(message), messageIdOf(message), correlationIdOf(message));
            handler.handle(mqMessage, ack);
        } catch (Exception ex) {
            try { session.recover(); } catch (JMSException ignore) {}
        }
    }

    private Destination resolveDestination(Session session, MQListenerDefinition def) throws JMSException {
        String topic = def.getTopic() == null ? "ddd4j.default.topic" : def.getTopic();
        String tag = MQTagMatcher.findIncludes(def.getTags()).stream().findFirst().orElse(null);
        String physical = tag == null ? topic : topic + "." + tag;
        if (def.getNamespace() != null && !def.getNamespace().isBlank()) {
            physical = def.getNamespace() + "." + physical;
        }
        return session.createTopic(physical);
    }

    private static String extractTag(Message message, MQListenerDefinition def) {
        try {
            String tag = message.getStringProperty(MQMessages.HEADER_DESTINATION_TAG);
            if (tag != null) return tag;
        } catch (JMSException ignore) {}
        return null;
    }

    private MQMessage<String> toMessage(Message message, Session session) throws JMSException {
        Map<String, Object> headers = new HashMap<>();
        if (message.getStringProperty(MQMessages.HEADER_DESTINATION_TOPIC) != null) {
            headers.put(MQMessages.HEADER_DESTINATION_TOPIC, message.getStringProperty(MQMessages.HEADER_DESTINATION_TOPIC));
        }
        if (message.getStringProperty(MQMessages.HEADER_DESTINATION_TAG) != null) {
            headers.put(MQMessages.HEADER_DESTINATION_TAG, message.getStringProperty(MQMessages.HEADER_DESTINATION_TAG));
        }
        if (message.getStringProperty(MQMessages.HEADER_TENANT_ID) != null) {
            headers.put(MQMessages.HEADER_TENANT_ID, message.getStringProperty(MQMessages.HEADER_TENANT_ID));
        }
        headers.put(ActiveMQMessageAcknowledgment.HEADER_AMQ_SESSION, session);
        headers.put(ActiveMQMessageAcknowledgment.HEADER_AMQ_MESSAGE, message);
        headers.put(ActiveMQMessageAcknowledgment.HEADER_AMQ_DELIVERY_ID, messageIdHash(message));

        String payload = "";
        if (message instanceof BytesMessage bm) {
            bm.reset();
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            payload = new String(buf, StandardCharsets.UTF_8);
        } else if (message instanceof TextMessage tm) {
            payload = tm.getText();
        }
        return MQMessage.of(payload, headers, messageIdOf(message), correlationIdOf(message), message);
    }

    private static String messageIdOf(Message message) {
        try { return message.getJMSMessageID(); } catch (JMSException e) { return null; }
    }
    private static String correlationIdOf(Message message) {
        try { return message.getJMSCorrelationID(); } catch (JMSException e) { return null; }
    }
    private static long messageIdHash(Message message) {
        try {
            String id = message.getJMSMessageID();
            return id == null ? 0L : (long) id.hashCode();
        } catch (JMSException e) { return 0L; }
    }
}
