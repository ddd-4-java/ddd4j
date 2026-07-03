package io.ddd4j.mq.activemq.consumer;

import io.ddd4j.mq.activemq.ack.ActiveMQMessageAcknowledgment;
import io.ddd4j.mq.activemq.config.ActiveMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQTagMatcher;
import jakarta.jms.*;

import java.lang.IllegalStateException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ActiveMQ (JMS) 消费者端点注册器（编程式注册）。
 *
 * <p>使用原生 JMS {@link MessageListener}（无需 Spring 监听容器）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ActiveMQConsumerEndpointRegistrar {

    /** JMS 连接实例 */
    private final Connection connection;
    /** ActiveMQ 配置属性 */
    private final ActiveMQProperties properties;

    /**
     * 构造 ActiveMQ 消费者端点注册器。
     *
     * @param connection JMS 连接
     * @param properties ActiveMQ 配置属性
     */
    public ActiveMQConsumerEndpointRegistrar(Connection connection, ActiveMQProperties properties) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 从 JMS 消息中提取标签（tag）信息。
     *
     * @param message JMS 消息
     * @param def     监听器定义
     * @return 标签字符串，如果不存在则返回 null
     */
    private static String extractTag(Message message, MQListenerDefinition def) {
        try {
            String tag = message.getStringProperty(MQMessages.HEADER_DESTINATION_TAG);
            if (Objects.nonNull(tag)) {
                return tag;
            }
        } catch (JMSException ignore) {
        }
        return null;
    }

    /**
     * 从 JMS 消息中获取消息 ID。
     *
     * @param message JMS 消息
     * @return 消息 ID，获取失败时返回 null
     */
    private static String messageIdOf(Message message) {
        try {
            return message.getJMSMessageID();
        } catch (JMSException e) {
            return null;
        }
    }

    /**
     * 从 JMS 消息中获取关联 ID。
     *
     * @param message JMS 消息
     * @return 关联 ID，获取失败时返回 null
     */
    private static String correlationIdOf(Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (JMSException e) {
            return null;
        }
    }

    /**
     * 从 JMS 消息 ID 计算哈希值作为投递标签。
     *
     * @param message JMS 消息
     * @return 投递标签哈希值
     */
    private static long messageIdHash(Message message) {
        try {
            String id = message.getJMSMessageID();
            return Objects.isNull(id) ? 0L : (long) id.hashCode();
        } catch (JMSException e) {
            return 0L;
        }
    }

    /**
     * 注册 MQ 监听器到 ActiveMQ 消费者端点。
     *
     * @param definition 监听器定义
     * @param handler    消费处理器
     * @throws IllegalStateException 如果注册失败
     */
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
                try {
                    message.acknowledge();
                } catch (JMSException ignore) {
                }
                return;
            }
            MQMessage<String> mqMessage = toMessage(message, session);
            ActiveMQMessageAcknowledgment ack = new ActiveMQMessageAcknowledgment(
                    session, message, messageIdHash(message), messageIdOf(message), correlationIdOf(message));
            handler.handle(mqMessage, ack);
        } catch (Exception ex) {
            try {
                session.recover();
            } catch (JMSException ignore) {
            }
        }
    }

    private Destination resolveDestination(Session session, MQListenerDefinition def) throws JMSException {
        String topic = Objects.isNull(def.getTopic()) ? "ddd4j.default.topic" : def.getTopic();
        String tag = MQTagMatcher.findIncludes(def.getTags()).stream().findFirst().orElse(null);
        String physical = Objects.isNull(tag) ? topic : topic + "." + tag;
        if (Objects.nonNull(def.getNamespace()) && !io.ddd4j.kit.lang.StrKit.isBlank(def.getNamespace())) {
            physical = def.getNamespace() + "." + physical;
        }
        return session.createTopic(physical);
    }

    private MQMessage<String> toMessage(Message message, Session session) throws JMSException {
        Map<String, Object> headers = new HashMap<>();
        if (Objects.nonNull(message.getStringProperty(MQMessages.HEADER_DESTINATION_TOPIC))) {
            headers.put(MQMessages.HEADER_DESTINATION_TOPIC, message.getStringProperty(MQMessages.HEADER_DESTINATION_TOPIC));
        }
        if (Objects.nonNull(message.getStringProperty(MQMessages.HEADER_DESTINATION_TAG))) {
            headers.put(MQMessages.HEADER_DESTINATION_TAG, message.getStringProperty(MQMessages.HEADER_DESTINATION_TAG));
        }
        if (Objects.nonNull(message.getStringProperty(MQMessages.HEADER_TENANT_ID))) {
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
}
