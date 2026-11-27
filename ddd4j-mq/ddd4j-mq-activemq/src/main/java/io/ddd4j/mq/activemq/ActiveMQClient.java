/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.mq.activemq;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.activemq.util.ActivemqKit;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
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
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("activemq");

    /**
     * 双构造 1：注入外部已配置好的 ActiveMQConnectionFactory（runtime 集成用）。
     * properties 默认为空，因为 properties 仅用于 publish 时的 message property 写入。
     *
     * @param connectionFactory 已配置的 ActiveMQ 连接工厂
     */
    public ActiveMQClient(ActiveMQConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "ActiveMQ ConnectionFactory is required");
        this.properties = new ActiveMQProperties();
    }

    /**
     * 双构造 2：从 properties 自建 ActiveMQConnectionFactory。
     *
     * @param properties ActiveMQ 连接属性
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
     * \u5173\u95ed\u539f\u751f\u6d88\u8d39\u8005\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param consumer \u672c\u6b21\u64cd\u4f5c\u6d89\u53ca\u7684\u539f\u751f\u6d88\u8d39\u8005
     */
    private static void closeConsumer(MessageConsumer consumer) {
        try {
            consumer.close();
        } catch (JMSException exception) {
            throw new IllegalStateException("Close ActiveMQ consumer failed", exception);
        }
    }

    /**
     * \u5173\u95ed\u539f\u751f\u6d88\u606f\u4f1a\u8bdd\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param session \u672c\u6b21\u64cd\u4f5c\u6d89\u53ca\u7684\u539f\u751f\u6d88\u606f\u4f1a\u8bdd
     */
    private static void closeSession(Session session) {
        try {
            session.close();
        } catch (JMSException exception) {
            throw new IllegalStateException("Close ActiveMQ session failed", exception);
        }
    }

    /**
     * \u5173\u95ed\u539f\u751f\u4ee3\u7406\u8fde\u63a5\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param connection \u5f85\u91ca\u653e\u6216\u7b49\u5f85\u5b8c\u6210\u7684\u539f\u751f\u8fde\u63a5
     */
    private static void closeConnection(Connection connection) {
        try {
            connection.close();
        } catch (JMSException exception) {
            throw new IllegalStateException("Close ActiveMQ connection failed", exception);
        }
    }

    // ========================= 消费者 =========================

    /**
     * \u4f18\u5148\u8bfb\u53d6 ddd4j \u6807\u51c6\u6d88\u606f ID\uff0c\u517c\u5bb9\u5347\u7ea7\u671f\u65e7\u952e\u5e76\u6700\u7ec8\u56de\u9000\u5230 JMS \u539f\u751f ID\u3002
     *
     * @param message \u6536\u5230\u7684 JMS \u6d88\u606f
     * @return \u7528\u4e8e\u786e\u8ba4\u548c\u65e5\u5fd7\u8bb0\u5f55\u7684 JMS \u6d88\u606f\u6807\u8bc6
     * @throws JMSException \u65e0\u6cd5\u8bfb\u53d6 JMS \u6d88\u606f\u7684\u6807\u8bc6\u5c5e\u6027
     */
    static String messageId(Message message) throws JMSException {
        String messageId = message.getStringProperty(jmsProperty(MessageHeaders.HEADER_MESSAGE_ID));
        if (Objects.isNull(messageId)) {
            messageId = message.getStringProperty(jmsProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID));
        }
        return Objects.nonNull(messageId) ? messageId : ActivemqKit.messageIdOf(message);
    }

    // ========================= 连接管理（双构造共享的最小辅助）=========================

    /**
     * ddd4j \u6d88\u606f\u5934\u7edf\u4e00\u4f7f\u7528 {@code ddd4j.xxx.yyy} \u547d\u540d\uff0cJMS \u573a\u666f\u9700\u66ff\u6362\u4e3a '_'\u3002
     *
     * @param name \u539f\u59cb\u5e94\u7528\u5c5e\u6027\u540d
     * @return \u53ef\u7528\u4f5c JMS \u5c5e\u6027\u540d\u7684\u5b57\u7b26\u4e32
     */
    static String jmsProperty(String name) {
        // JMS 属性名必须是合法 Java 标识符：'.' 与 '-' 均非法，统一替换为 '_'
        return name.replace('.', '_').replace('-', '_');
    }

    /** {@inheritDoc} */
    @Override
    public String impl() {
        return "activemq";
    }

    /** {@inheritDoc} */
    @Override
    public MQClientLifecycle lifecycle() {
        return lifecycle;
    }

    /** {@inheritDoc} */
    @Override
    public MQStartupStatus startupStatus() {
        return startupStatus;
    }

    /** {@inheritDoc} */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            // Producer-only session：AUTO_ACKNOWLEDGE 参数对 producer 无意义（JMS ack 模式仅对 consumer 生效），
            // 避免 ACK 语义被框架自动接管导致手动 ack 失效。
            final Session session = getConnection().createSession(false, Session.AUTO_ACKNOWLEDGE);
            lifecycle.register("activemq-producer-session", () -> closeSession(session));
            return mqEvent -> {
                String payload = serialization().serialize(mqEvent);
                String topic = resolveTopic(mqEvent, mqProperties);
                try (MessageProducer producer = session.createProducer(ActivemqKit.createDestination(session, topic))) {
                    producer.setDeliveryMode(properties.isDurable() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                    BytesMessage message = session.createBytesMessage();
                    message.writeBytes(payload.getBytes(StandardCharsets.UTF_8));
                    message.setStringProperty(jmsProperty(MessageHeaders.HEADER_DESTINATION_TOPIC), mqEvent.getTopic());
                    if (Objects.nonNull(mqEvent.getTag())) {
                        // tag header 与 broker 端 selector property name 一致（无 .，合法 SQL-92）
                        message.setStringProperty(jmsProperty(tagHeaderKey()), mqEvent.getTag());
                    }
                    if (Objects.nonNull(mqEvent.getTenantId())) {
                        message.setStringProperty(jmsProperty(MessageHeaders.HEADER_TENANT_ID), mqEvent.getTenantId());
                    }
                    if (Objects.nonNull(mqEvent.getMsgId())) {
                        // JMSMessageID 必须以 "ID:" 开头（Artemis checkProperty 校验）
                        message.setJMSMessageID("ID:" + mqEvent.getMsgId());
                        message.setStringProperty(jmsProperty(MessageHeaders.HEADER_MESSAGE_ID), mqEvent.getMsgId());
                    }
                    if (StrKit.isNotEmpty(mqEvent.getCorrelationId())) {
                        message.setStringProperty(jmsProperty(MQDeliveryHeaders.CORRELATION_ID), mqEvent.getCorrelationId());
                        message.setJMSCorrelationID(mqEvent.getCorrelationId());
                    }
                    if (StrKit.isNotEmpty(mqEvent.getCausationId())) {
                        message.setStringProperty(jmsProperty(MQDeliveryHeaders.CAUSATION_ID), mqEvent.getCausationId());
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

    /** {@inheritDoc} */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        final Session session = getConnection().createSession(false, Session.CLIENT_ACKNOWLEDGE);
        lifecycle.register("activemq-consumer-session", () -> closeSession(session));
        String topic = resolveTopic(listener, mqProperties);
        // broker 端 tag 过滤：把 MQEventListener.tags 表达式翻译成 JMS Message Selector，
        // 不匹配的消息在 broker 端就过滤掉，不投递到 listener（broker 端精确过滤）。
        String selector = tagsToSelector(listener.getTags());
        MessageConsumer consumer = session.createConsumer(ActivemqKit.createDestination(session, topic), selector);
        lifecycle.register("activemq-consumer", () -> closeConsumer(consumer));
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
                String correlationId = message.getStringProperty(jmsProperty(MQDeliveryHeaders.CORRELATION_ID));
                if (Objects.isNull(correlationId)) {
                    correlationId = ActivemqKit.correlationIdOf(message);
                }
                if (Objects.nonNull(correlationId)) {
                    event.setCorrelationId(correlationId);
                }
                String causationId = message.getStringProperty(jmsProperty(MQDeliveryHeaders.CAUSATION_ID));
                if (Objects.nonNull(causationId)) {
                    event.setCausationId(causationId);
                }
                ActiveMQAcknowledgment ack = new ActiveMQAcknowledgment(
                        session, message,
                        ActivemqKit.messageIdHash(message),
                        ActivemqKit.messageIdOf(message),
                        event.getCorrelationId());
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
                try {
                    nc.start();
                } catch (JMSException startFailure) {
                    try {
                        nc.close();
                    } catch (JMSException closeFailure) {
                        startFailure.addSuppressed(closeFailure);
                    }
                    throw startFailure;
                }
                connectionRef.set(nc);
                lifecycle.register("activemq-connection", () -> closeConnection(nc));
                connection = nc;
            } catch (JMSException ex) {
                throw new IllegalStateException("Open ActiveMQ connection failed", ex);
            }
        }
        log.info("Get ActiveMQ connection: {}", connection);
        return connection;
    }

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
    @Override
    public void close() {
        try {
            lifecycle.close();
        } finally {
            startupStatus.stopped();
        }
    }
}
