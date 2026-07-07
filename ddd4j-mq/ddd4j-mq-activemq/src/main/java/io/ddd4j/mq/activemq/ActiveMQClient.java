package io.ddd4j.mq.activemq;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import jakarta.jms.*;

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

    private final ActiveMQProperties properties;
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();

    public ActiveMQClient(ActiveMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "activemq";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            Session session = connection().createSession(false, Session.AUTO_ACKNOWLEDGE);
            return event -> publish(session, event, mqProperties);
        } catch (JMSException ex) {
            throw new java.lang.IllegalStateException("Init ActiveMQ producer failed", ex);
        }
    }

    private void publish(Session session, MQEvent event, MQProperties mqProperties) {
        try {
            String physical = resolvePhysical(event.getNamespace(), event.getTopic(), event.getTag());
            jakarta.jms.Destination target = createDestination(session, physical);
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
        Connection connection = connection();
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        String physical = resolvePhysical(listener.getNamespace(), listener.getTopic(),
                TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null));
        jakarta.jms.Destination destination = createDestination(session, physical);
        MessageConsumer consumer = session.createConsumer(destination);
        consumer.setMessageListener((Message message) -> handleMessage(session, message, listener));
        return true;
    }

    private void handleMessage(Session session, Message message, MQListener listener) {
        try {
            String tag = stringProperty(message, MessageHeaders.HEADER_DESTINATION_TAG);
            if (!TagMatcher.match(tag, listener.getTags())) {
                try {
                    message.acknowledge();
                } catch (JMSException ignore) {
                }
                return;
            }
            String payload = extractPayload(message);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            Acknowledgment ack = new ActiveMQAcknowledgment(
                    session, message, messageIdHash(message), messageIdOf(message), correlationIdOf(message));
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

    // ========================= JMS 工具 =========================

    private static String resolvePhysical(String namespace, String topic, String tag) {
        String base = io.ddd4j.kit.lang.StrKit.hasText(topic) ? topic : "ddd4j.default.topic";
        if (io.ddd4j.kit.lang.StrKit.hasText(namespace)) {
            base = namespace + "." + base;
        }
        return io.ddd4j.kit.lang.StrKit.hasText(tag) ? base + "." + tag : base;
    }

    private static jakarta.jms.Destination createDestination(Session session, String physical) throws JMSException {
        if (physical.startsWith("queue:")) {
            return session.createQueue(physical.substring("queue:".length()));
        }
        if (physical.startsWith("topic:")) {
            return session.createTopic(physical.substring("topic:".length()));
        }
        return session.createTopic(physical);
    }

    private static String extractPayload(Message message) throws JMSException {
        if (message instanceof BytesMessage bm) {
            bm.reset();
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
        if (message instanceof TextMessage tm) {
            return tm.getText();
        }
        return "";
    }

    private static String stringProperty(Message message, String key) {
        try {
            return message.getStringProperty(key);
        } catch (JMSException ignore) {
            return null;
        }
    }

    private static String messageIdOf(Message message) {
        try {
            return message.getJMSMessageID();
        } catch (JMSException e) {
            return null;
        }
    }

    private static String correlationIdOf(Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (JMSException e) {
            return null;
        }
    }

    private static long messageIdHash(Message message) {
        try {
            String id = message.getJMSMessageID();
            return Objects.isNull(id) ? 0L : (long) id.hashCode();
        } catch (JMSException e) {
            return 0L;
        }
    }

    private synchronized Connection connection() {
        Connection c = connectionRef.get();
        if (Objects.isNull(c)) {
            try {
                Connection nc = properties.connectionFactory().createConnection();
                nc.start();
                connectionRef.set(nc);
                c = nc;
            } catch (JMSException ex) {
                throw new java.lang.IllegalStateException("Open ActiveMQ connection failed", ex);
            }
        }
        return c;
    }
}
