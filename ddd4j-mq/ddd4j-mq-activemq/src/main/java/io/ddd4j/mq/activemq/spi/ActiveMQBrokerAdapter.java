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
 * ActiveMQ Classic Broker 适配器（纯 Java，零 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ActiveMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    /** ActiveMQ 配置属性 */
    private final ActiveMQProperties properties;
    /** MQ 全局配置 */
    private final Ddd4jMQProperties mqProperties;
    /** 事件序列化器 */
    private final MQEventSerialization serialization;
    /** JMS 连接引用（线程安全） */
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();
    /** 消费者端点注册器 */
    private final ActiveMQConsumerEndpointRegistrar consumerRegistrar;

    /**
     * 构造 ActiveMQ Broker 适配器，使用默认 JSON 序列化器。
     *
     * @param properties   ActiveMQ 配置属性
     * @param mqProperties MQ 全局配置
     */
    public ActiveMQBrokerAdapter(ActiveMQProperties properties, Ddd4jMQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQMessageSerialization());
    }

    /**
     * 构造 ActiveMQ Broker 适配器。
     *
     * @param properties   ActiveMQ 配置属性
     * @param mqProperties MQ 全局配置
     * @param serialization 事件序列化器
     */
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
            return new ActiveMQEventPublisher(connection(), session, properties, Objects.isNull(props) ? mqProperties : props, serialization);
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
        if (Objects.isNull(message)) {
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
        if (Objects.nonNull(c)) {
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
            if (Objects.isNull(c)) {
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
