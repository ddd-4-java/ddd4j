package io.ddd4j.mq.activemq;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.activemq.util.ActivemqKit;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import jakarta.jms.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import java.lang.IllegalStateException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * ActiveMQ (JMS) 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer} —— 建 JMS Session，返回 {@link Consumer<MQEvent>}，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— 建 JMS 消费者，tag 过滤后调 {@link #consume} 统一消费，
 *       传入 {@link ActiveMQAcknowledgment} 实现不同级别 ack</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class ActiveMQClient implements MQClient {

    private final ActiveMQConnectionFactory connectionFactory;
    private final ActiveMQProperties properties;
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();

    public ActiveMQClient(ActiveMQConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "ActiveMQ ConnectionFactory is required");
        this.properties = new ActiveMQProperties();
    }

    public ActiveMQClient(ActiveMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "ActiveMQ Properties is required");
        this.connectionFactory = this.getConnectionFactory();
        logger().info("Init ActiveMQ client with {}", properties);
    }

    @Override
    public String impl() {
        return "activemq";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            Session session = getConnection().createSession(false, Session.AUTO_ACKNOWLEDGE);
            return event -> publish(session, event, mqProperties);
        } catch (JMSException ex) {
            throw new java.lang.IllegalStateException("Init ActiveMQ producer failed", ex);
        }
    }

    private void publish(Session session, MQEvent event, MQProperties mqProperties) {
        try {
            String physical = ActivemqKit.resolvePhysical(event.getNamespace(), event.getTopic(), event.getTag());
            Destination target = ActivemqKit.createDestination(session, physical);
            try (MessageProducer producer = session.createProducer(target)) {
                producer.setDeliveryMode(properties.isDurable() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                BytesMessage message = session.createBytesMessage();
                message.writeBytes(serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8));
                message.setStringProperty(MessageHeaders.HEADER_DESTINATION_TOPIC, event.getTopic());
                if (Objects.nonNull(event.getTag())) {
                    message.setStringProperty(MessageHeaders.HEADER_DESTINATION_TAG, event.getTag());
                }
                if (Objects.nonNull(event.getTenantId())) {
                    message.setStringProperty(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
                }
                if (Objects.nonNull(event.getMsgId())) {
                    message.setJMSMessageID(event.getMsgId());
                }
                producer.send(message);
            }
            logger().info("Publish MQ [{}]: {}", event.getTopic(), serialization().serialize(event));
        } catch (JMSException ex) {
            throw new java.lang.IllegalStateException("Publish ActiveMQ event failed", ex);
        }
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        Connection connection = getConnection();
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        String physical = ActivemqKit.resolvePhysical(listener.getNamespace(), listener.getTopic(),
                TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null));
        Destination destination = ActivemqKit.createDestination(session, physical);
        MessageConsumer consumer = session.createConsumer(destination);
        consumer.setMessageListener((Message message) -> handleMessage(session, message, listener));
        return true;
    }

    private void handleMessage(Session session, Message message, MQListener listener) {
        try {
            String tag = ActivemqKit.stringProperty(message, MessageHeaders.HEADER_DESTINATION_TAG);
            if (!TagMatcher.match(tag, listener.getTags())) {
                try {
                    message.acknowledge();
                } catch (JMSException ignore) {
                }
                return;
            }
            String payload = ActivemqKit.extractPayload(message);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            Acknowledgment ack = new ActiveMQAcknowledgment(session, message,
                    ActivemqKit.messageIdHash(message), ActivemqKit.messageIdOf(message), ActivemqKit.correlationIdOf(message));
            consume(listener, event, ack);
            if (!ack.isAcknowledged()) {
                ack.ackSingle();
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
            try {
                session.recover();
            } catch (JMSException ignore) {
            }
        }
    }


    private synchronized Connection getConnection() {
        Connection connection = connectionRef.get();
        if (Objects.isNull(connection) && Objects.nonNull(connectionFactory)) {
            try {
                Connection nc = connectionFactory.createConnection();
                nc.start();
                connectionRef.set(nc);
                connection = nc;
            } catch (JMSException ex) {
                throw new IllegalStateException("Open ActiveMQ connection failed", ex);
            }
        }
        return connection;
    }


    /**
     * 创建并配置 ActiveMQ 连接工厂。
     *
     * @return 配置好的连接工厂实例
     */
    public ActiveMQConnectionFactory getConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(properties.getBrokerUrl());
        if (Objects.nonNull(properties.getUsername()) && !io.ddd4j.kit.lang.StrKit.isBlank(properties.getUsername())) {
            factory.setUser(properties.getUsername());
        }
        if (Objects.nonNull(properties.getPassword()) && !io.ddd4j.kit.lang.StrKit.isBlank(properties.getPassword())) {
            factory.setPassword(properties.getPassword());
        }
        factory.setClientID(Objects.requireNonNullElse(properties.getClientIdPrefix(), "ddd4j-mq-"));
        return factory;
    }
}
