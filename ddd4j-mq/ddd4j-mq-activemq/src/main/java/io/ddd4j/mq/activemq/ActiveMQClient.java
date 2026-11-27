package io.ddd4j.mq.activemq;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.activemq.util.ActivemqKit;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import jakarta.jms.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import java.lang.IllegalStateException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * ActiveMQ (JMS) 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 * 工具逻辑（payload 提取、messageId 读取、物理地址拼接、createQueue/createTopic 分支）下沉到
 * 无状态的 {@link ActivemqKit}，ack 映射下沉到 {@link ActiveMQAcknowledgment}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : ActiveMQClient ###")
public class ActiveMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final ActiveMQProperties properties;
    /**
     * 延迟初始化的 ActiveMQ 连接工厂（构造 1 注入时不创建）
     */
    private final ActiveMQConnectionFactory connectionFactory;
    /**
     * 共享 connection（第一个 init* 时懒构造）
     */
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();

    /**
     * \u53cc\u6784\u9020 1\uff1a\u6ce8\u5165\u5916\u90e8\u5df2\u914d\u7f6e\u597d\u7684 ActiveMQConnectionFactory\uff08runtime \u96c6\u6210\u7528\uff09\u3002
     *  properties \u9ed8\u8ba4\u4e3a\u7a7a\uff0c\u56e0\u4e3a properties \u4ec5\u7528\u4e8e publish \u65f6\u7684 message property \u5199\u5165\u3002
     *
     * @param connectionFactory \u5df2\u914d\u7f6e\u7684 ActiveMQ \u8fde\u63a5\u5de5\u5382
     */
    public ActiveMQClient(ActiveMQConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "ActiveMQ ConnectionFactory is required");
        this.properties = new ActiveMQProperties();
    }

    /**
     * \u53cc\u6784\u9020 2\uff1a\u4ece properties \u81ea\u5efa ActiveMQConnectionFactory\u3002
     *
     * @param properties ActiveMQ \u8fde\u63a5\u5c5e\u6027
     */
    public ActiveMQClient(ActiveMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "ActiveMQ Properties is required");
        this.connectionFactory = buildFactory(properties);
        log.info("Init ActiveMQ client with {}", properties);
    }

    /**
     * \u4ece properties \u81ea\u5efa ActiveMQConnectionFactory\uff08\u6784\u9020 2 \u7528\uff09\u3002
     *
     * @param properties ActiveMQ \u8fde\u63a5\u914d\u7f6e
     * @return \u914d\u7f6e\u597d\u7684 ActiveMQ \u8fde\u63a5\u5de5\u5382
     */
    private static ActiveMQConnectionFactory buildFactory(ActiveMQProperties properties) {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(properties.getBrokerUrl());
        if (Objects.nonNull(properties.getUsername()) && StrKit.isNotBlank(properties.getUsername())) {
            factory.setUser(properties.getUsername());
        }
        if (Objects.nonNull(properties.getPassword()) && StrKit.isNotBlank(properties.getPassword())) {
            factory.setPassword(properties.getPassword());
        }
        factory.setClientID(Objects.requireNonNullElse(properties.getClientIdPrefix(), "ddd4j-mq-"));
        return factory;
    }

    // ========================= 生产者 =========================

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"activemq"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "activemq";
    }

    // ========================= 消费者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>ActiveMQ 实现调用 <code>getConnection().createSession(false, Session.AUTO_ACKNOWLEDGE)</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            // Producer-only session：AUTO_ACKNOWLEDGE 参数对 producer 无意义（JMS ack 模式仅对 consumer 生效），
            // 避免 ACK 语义被框架自动接管导致手动 ack 失效。
            final Session session = getConnection().createSession(false, Session.AUTO_ACKNOWLEDGE);
            return mqEvent -> {
                String payload = serialization().serialize(mqEvent);
                String topic = resolveTopic(mqEvent, mqProperties);
                try (MessageProducer producer = session.createProducer(ActivemqKit.createDestination(session, topic))) {
                    producer.setDeliveryMode(properties.isDurable() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                    BytesMessage message = session.createBytesMessage();
                    message.writeBytes(payload.getBytes(StandardCharsets.UTF_8));
                    message.setStringProperty(MessageHeaders.HEADER_DESTINATION_TOPIC, mqEvent.getTopic());
                    if (Objects.nonNull(mqEvent.getTag())) {
                        // tag header 与 broker 端 selector property name 一致（无 .，合法 SQL-92）
                        message.setStringProperty(tagHeaderKey(), mqEvent.getTag());
                    }
                    if (Objects.nonNull(mqEvent.getTenantId())) {
                        message.setStringProperty(MessageHeaders.HEADER_TENANT_ID, mqEvent.getTenantId());
                    }
                    if (Objects.nonNull(mqEvent.getMsgId())) {
                        message.setJMSMessageID(mqEvent.getMsgId());
                        message.setStringProperty(MessageHeaders.HEADER_MESSAGE_ID, mqEvent.getMsgId());
                    }
                    producer.send(message);
                } catch (JMSException ex) {
                    log.error("Publish MQ [{}]: {} failed!", topic, payload, ex);
                    throw new IllegalStateException("Publish ActiveMQ event failed", ex);
                }
                log.info("Publish MQ [{}]: {}", topic, payload);
            };
        } catch (JMSException ex) {
            throw new IllegalStateException("Init ActiveMQ producer failed", ex);
        }
    }

    // ========================= 连接管理（双构造共享的最小辅助）=========================

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>ActiveMQ 实现调用 <code>getConnection().createSession(false, Session.CLIENT_ACKNOWLEDGE)</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        final Session session = getConnection().createSession(false, Session.CLIENT_ACKNOWLEDGE);
        String topic = resolveTopic(listener, mqProperties);
        // broker 端 tag 过滤：把 MQEventListener.tags 表达式翻译成 JMS Message Selector，
        // 不匹配的消息在 broker 端就过滤掉，不投递到 listener（broker 端精确过滤）。
        String selector = tagsToSelector(listener.getTags());
        MessageConsumer consumer = session.createConsumer(ActivemqKit.createDestination(session, topic), selector);
        consumer.setMessageListener(message -> {
            try {
                MQEvent event = serialization().deserialize(ActivemqKit.extractPayload(message), listener.payloadType());
                if (Objects.isNull(event)) {
                    log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(defaultConcat()));
                    return;
                }
                String messageId = messageId(message);
                if (Objects.nonNull(messageId)) {
                    event.setMsgId(messageId);
                }
                ActiveMQAcknowledgment ack = new ActiveMQAcknowledgment(
                        session, message,
                        ActivemqKit.messageIdHash(message),
                        ActivemqKit.messageIdOf(message),
                        ActivemqKit.correlationIdOf(message));
                this.consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                log.error("Consume MQ [{}] failed", listener.getRouteExpression(defaultConcat()), ex);
                try {
                    session.recover();
                } catch (JMSException ignore) {
                }
            }
        });
        return true;
    }

    /**
     * \u53d6\u5f97\u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef\u3002
     * @return \u4f9b\u53d1\u5e03\u6216\u6d88\u8d39\u64cd\u4f5c\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef
     */
    private synchronized Connection getConnection() {
        Connection connection = connectionRef.get();
        if (Objects.isNull(connection)) {
            try {
                Connection nc = connectionFactory.createConnection();
                nc.start();
                connectionRef.set(nc);
                connection = nc;
            } catch (JMSException ex) {
                throw new IllegalStateException("Open ActiveMQ connection failed", ex);
            }
        }
        log.info("Get ActiveMQ connection: {}", connection);
        return connection;
    }

    /**
     * \u4f18\u5148\u8bfb\u53d6 ddd4j \u6807\u51c6\u6d88\u606f ID\uff0c\u517c\u5bb9\u5347\u7ea7\u671f\u65e7\u952e\u5e76\u6700\u7ec8\u56de\u9000\u5230 JMS \u539f\u751f ID\u3002
     *
     * @param message \u6536\u5230\u7684 JMS \u6d88\u606f
     * @return \u7528\u4e8e\u786e\u8ba4\u548c\u65e5\u5fd7\u8bb0\u5f55\u7684 JMS \u6d88\u606f\u6807\u8bc6
     * @throws JMSException \u65e0\u6cd5\u8bfb\u53d6 JMS \u6d88\u606f\u7684\u6807\u8bc6\u5c5e\u6027
     */
    static String messageId(Message message) throws JMSException {
        String messageId = message.getStringProperty(MessageHeaders.HEADER_MESSAGE_ID);
        if (Objects.isNull(messageId)) {
            messageId = message.getStringProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
        }
        return Objects.nonNull(messageId) ? messageId : ActivemqKit.messageIdOf(message);
    }
}
