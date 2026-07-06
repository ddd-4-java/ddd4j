package io.ddd4j.mq.activemq.publisher;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.activemq.config.ActiveMQProperties;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.serialization.EventSerialization;
import jakarta.jms.*;

import java.lang.IllegalStateException;
import java.util.Objects;

/**
 * ActiveMQ 事件发布器（纯 Java，JMS API）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ActiveEventPublisher implements EventPublisher {

    /** ActiveMQ 配置属性 */
    private final ActiveMQProperties properties;
    /** MQ 全局配置 */
    private final MQProperties mqProperties;
    /** 事件序列化器 */
    private final EventSerialization serialization;
    /** JMS Session 实例 */
    private final Session session;

    /**
     * 构造 ActiveMQ 事件发布器。
     *
     * @param connection    JMS 连接
     * @param session       JMS Session
     * @param properties    ActiveMQ 配置属性
     * @param mqProperties  MQ 全局配置
     * @param serialization 事件序列化器
     */
    public ActiveEventPublisher(Connection connection, Session session,
                                  ActiveMQProperties properties,
                                  MQProperties mqProperties,
                                  EventSerialization serialization) {
        this.session = Objects.requireNonNull(session, "session");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    /**
     * 解析 {@link Destination} 为 JMS Destination。
     *
     * <p>约定：{@code namespace} 为前缀（解析成 {@code topic}/{@code queue} 子名），{@code tag} 为后缀。
     * 通过字符串前缀 {@code queue:} / {@code topic:} 可强制类型，否则根据 tag 是否以 {@code queue.} 开头判定。
     *
     * @param session     JMS Session
     * @param destination MQ 目标地址
     * @return JMS Destination 对象
     * @throws JMSException 如果解析失败
     */
    static Destination resolveDestination(Session session, Destination destination) throws JMSException {
        String topic = StrKit.hasText(destination.getTopic()) ? destination.getTopic() : "ddd4j.default.topic";
        String tag = destination.getTag();
        String physical = StrKit.hasText(tag) ? topic + "." + tag : topic;
        // JMS 没有 native namespace 概念 —— 用前缀表达
        String ns = destination.getNamespace();
        if (StrKit.hasText(ns)) {
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

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        try {
            DestinationResolver.fillDefaults(event, mqProperties);
            Destination target = resolveDestination(session, destination);
            try (MessageProducer producer = session.createProducer(target)) {
                producer.setDeliveryMode(properties.isDurable() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                BytesMessage message = session.createBytesMessage();
                message.writeBytes(serialization.serialize(event).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                message.setStringProperty(MessageHeaders.HEADER_DESTINATION_TOPIC, destination.getTopic());
                if (Objects.nonNull(destination.getTag())) {
                    message.setStringProperty(MessageHeaders.HEADER_DESTINATION_TAG, destination.getTag());
                }
                if (Objects.nonNull(event.getTenantId())) {
                    message.setStringProperty(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
                }
                if (Objects.nonNull(event.getMsgId())) {
                    message.setJMSMessageID(event.getMsgId());
                }
                producer.send(message);
            }
        } catch (JMSException ex) {
            throw new IllegalStateException("Publish ActiveMQ event failed", ex);
        }
    }
}
