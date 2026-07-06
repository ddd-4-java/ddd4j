package io.ddd4j.mq.activemq.spi;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.activemq.ack.ActiveMQAcknowledgment;
import io.ddd4j.mq.activemq.config.ActiveMQProperties;
import io.ddd4j.mq.activemq.consumer.ActiveMQConsumerEndpointRegistrar;
import io.ddd4j.mq.activemq.publisher.ActiveEventPublisher;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;

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
public class ActiveBrokerAdapter implements BrokerAdapter, AutoCloseable {

    /** ActiveMQ 配置属性 */
    private final ActiveMQProperties properties;
    /** MQ 全局配置 */
    private final MQProperties mqProperties;
    /** 事件序列化器 */
    private final EventSerialization serialization;
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
    public ActiveBrokerAdapter(ActiveMQProperties properties, MQProperties mqProperties) {
        this(properties, mqProperties, new JsonSerialization());
    }

    /**
     * 构造 ActiveMQ Broker 适配器。
     *
     * @param properties   ActiveMQ 配置属性
     * @param mqProperties MQ 全局配置
     * @param serialization 事件序列化器
     */
    public ActiveBrokerAdapter(ActiveMQProperties properties, MQProperties mqProperties,
                                 EventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.consumerRegistrar = new ActiveMQConsumerEndpointRegistrar(connection(), properties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.ACTIVEMQ;
    }

    @Override
    public EventPublisher createPublisher(MQProperties props) {
        try {
            Session session = connection().createSession(false, Session.AUTO_ACKNOWLEDGE);
            return new ActiveEventPublisher(connection(), session, properties, Objects.isNull(props) ? mqProperties : props, serialization);
        } catch (JMSException ex) {
            throw new IllegalStateException("Create ActiveMQ publisher failed", ex);
        }
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        Object session = message.header(ActiveMQAcknowledgment.HEADER_AMQ_SESSION);
        Object msg = message.header(ActiveMQAcknowledgment.HEADER_AMQ_MESSAGE);
        if (session instanceof Session s && msg instanceof Message m) {
            Object deliveryObj = message.header(ActiveMQAcknowledgment.HEADER_AMQ_DELIVERY_ID);
            long deliveryId = deliveryObj instanceof Number n ? n.longValue() : 0L;
            return new ActiveMQAcknowledgment(s, m, deliveryId,
                    message.getMessageId(), message.getCorrelationId());
        }
        return null;
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.ACTIVEMQ == configured;
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
