package io.ddd4j.mq.activemq.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.activemq.config.ActiveMQProperties;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import java.util.Objects;

/**
 * ActiveMQ event publisher (pure Java, JMS API).
 */
public class ActiveMQEventPublisher implements MQEventPublisher {

    private final ActiveMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final Connection connection;
    private final Session session;

    public ActiveMQEventPublisher(Connection connection, Session session,
                                  ActiveMQProperties properties,
                                  Ddd4jMQProperties mqProperties,
                                  MQEventSerialization serialization) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.session = Objects.requireNonNull(session, "session");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        try {
            Destination target = resolveDestination(session, destination);
            try (MessageProducer producer = session.createProducer(target)) {
                producer.setDeliveryMode(properties.isDurable() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                BytesMessage message = session.createBytesMessage();
                message.writeBytes(serialization.serialize(event).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                message.setStringProperty(MQMessages.HEADER_DESTINATION_TOPIC, destination.getTopic());
                if (destination.getTag() != null) {
                    message.setStringProperty(MQMessages.HEADER_DESTINATION_TAG, destination.getTag());
                }
                if (event.getTenantId() != null) {
                    message.setStringProperty(MQMessages.HEADER_TENANT_ID, event.getTenantId());
                }
                if (event.getMsgId() != null) {
                    message.setJMSMessageID(event.getMsgId());
                }
                producer.send(message);
            }
        } catch (JMSException ex) {
            throw new IllegalStateException("Publish ActiveMQ event failed", ex);
        }
    }

    /**
     * 解析 {@link MQDestination} 为 JMS Destination。
     * <p>约定：{@code namespace} 为前缀（解析成 {@code topic}/{@code queue} 子名），{@code tag} 为后缀。
     * 通过字符串前缀 {@code queue:} / {@code topic:} 可强制类型，否则根据 tag 是否以 {@code queue.} 开头判定。
     */
    static Destination resolveDestination(Session session, MQDestination destination) throws JMSException {
        String topic = firstText(destination.getTopic(), "ddd4j.default.topic");
        String tag = destination.getTag();
        String physical = tag == null || tag.isBlank() ? topic : topic + "." + tag;
        // JMS 没有 native namespace 概念 —— 用前缀表达
        String ns = destination.getNamespace();
        if (ns != null && !ns.isBlank()) {
            physical = ns + "." + physical;
        }
        if (physical.startsWith("queue:")) {
            return session.createQueue(physical.substring("queue:".length()));
        }
        if (physical.startsWith("topic:")) {
            return session.createTopic(physical.substring("topic:".length()));
        }
        // 默认 topic（ActiveMQ 主题订阅）
        return session.createTopic(physical);
    }

    private static String firstText(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
