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

    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
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
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
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
     * 从 properties 自建 ActiveMQConnectionFactory（构造 2 用）。
     *
     * @param properties ActiveMQ 连接配置
     * @return 配置好的 ActiveMQ 连接工厂
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
     * 关闭原生消费者，并处理原生客户端抛出的异常。
     *
     * @param consumer 本次操作涉及的原生消费者
     */
    private static void closeConsumer(MessageConsumer consumer) {
        try {
            consumer.close();
        } catch (JMSException exception) {
            throw new IllegalStateException("Close ActiveMQ consumer failed", exception);
        }
    }

    /**
     * 关闭原生消息会话，并处理原生客户端抛出的异常。
     *
     * @param session 本次操作涉及的原生消息会话
     */
    private static void closeSession(Session session) {
        try {
            session.close();
        } catch (JMSException exception) {
            throw new IllegalStateException("Close ActiveMQ session failed", exception);
        }
    }

    /**
     * 关闭原生代理连接，并处理原生客户端抛出的异常。
     *
     * @param connection 待释放或等待完成的原生连接
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
     * 优先读取 ddd4j 标准消息 ID，兼容升级期旧键并最终回退到 JMS 原生 ID。
     *
     * @param message 收到的 JMS 消息
     * @return 用于确认和日志记录的 JMS 消息标识
     * @throws JMSException 无法读取 JMS 消息的标识属性
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
     * ddd4j 消息头统一使用 {@code ddd4j.xxx.yyy} 命名，JMS 场景需替换为 '_'。
     *
     * @param name 原始应用属性名
     * @return 可用作 JMS 属性名的字符串
     */
    static String jmsProperty(String name) {
        // JMS 属性名必须是合法 Java 标识符：'.' 与 '-' 均非法，统一替换为 '_'
        return name.replace('.', '_').replace('-', '_');
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"activemq"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "activemq";
    }

    /**
     * 返回管理连接、生产者和消费者资源的生命周期对象。
     *
     * @return 当前消息适配器用于登记和关闭资源的生命周期对象
     */
    @Override
    public MQClientLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * 返回消息客户端当前记录的启动状态。
     *
     * @return 当前消息适配器记录的启动状态
     */
    @Override
    public MQStartupStatus startupStatus() {
        return startupStatus;
    }

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

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>ActiveMQ 实现调用 <code>getConnection().createSession(false, Session.CLIENT_ACKNOWLEDGE)</code>。</p>
     *
     * @param listener     监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
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
     * 取得当前适配器使用的原生连接或客户端。
     *
     * @return 供发布或消费操作使用的原生连接或客户端
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

    /**
     * 释放消息客户端拥有的运行资源，并在退出时登记组件已停止。
     */
    @Override
    public void close() {
        try {
            lifecycle.close();
        } finally {
            startupStatus.stopped();
        }
    }
}
