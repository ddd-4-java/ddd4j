package io.ddd4j.mq.activemq.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.activemq.ack.ActiveMQMessageAcknowledgment;
import io.ddd4j.mq.activemq.config.ActiveMQProperties;
import io.ddd4j.mq.activemq.consumer.ActiveMQConsumerEndpointRegistrar;
import io.ddd4j.mq.activemq.publisher.ActiveMQEventPublisher;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ActiveMQ Classic broker adapter (pure Java, zero Spring).
 */
public class ActiveMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final ActiveMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();
    private final ActiveMQConsumerEndpointRegistrar consumerRegistrar;

    public ActiveMQBrokerAdapter(ActiveMQProperties properties, Ddd4jMQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQMessageSerialization());
    }

    public ActiveMQBrokerAdapter(ActiveMQProperties properties, Ddd4jMQProperties mqProperties,
                                 MQEventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.consumerRegistrar = new ActiveMQConsumerEndpointRegistrar(connection(), properties);
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ACTIVEMQ;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        try {
            Session session = connection().createSession(false, Session.AUTO_ACKNOWLEDGE);
            return new ActiveMQEventPublisher(connection(), session, properties, java.util.Objects.isNull(props) ? mqProperties : props, serialization);
        } catch (JMSException ex) {
            throw new IllegalStateException("Create ActiveMQ publisher failed", ex);
        }
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        if (java.util.Objects.isNull(message)) {
            return null;
        }
        Object session = message.header(ActiveMQMessageAcknowledgment.HEADER_AMQ_SESSION);
        Object msg = message.header(ActiveMQMessageAcknowledgment.HEADER_AMQ_MESSAGE);
        if (session instanceof Session s && msg instanceof Message m) {
            Object deliveryObj = message.header(ActiveMQMessageAcknowledgment.HEADER_AMQ_DELIVERY_ID);
            long deliveryId = deliveryObj instanceof Number n ? n.longValue() : 0L;
            return new ActiveMQMessageAcknowledgment(s, m, deliveryId,
                    message.getMessageId(), message.getCorrelationId());
        }
        return null;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.ACTIVEMQ == configured;
    }

    @Override
    public void close() throws Exception {
        Connection c = connectionRef.get();
        if (java.util.Objects.nonNull(c)) {
            try {
                c.close();
            } finally {
                connectionRef.set(null);
            }
        }
    }

    private synchronized Connection connection() {
        Connection c = connectionRef.get();
        try {
            if (java.util.Objects.isNull(c)) {
                Connection nc = properties.connectionFactory().createConnection();
                nc.start();
                connectionRef.set(nc);
                c = nc;
            }
        } catch (JMSException ex) {
            throw new IllegalStateException("Open ActiveMQ connection failed", ex);
        }
        return c;
    }
}
