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
package io.ddd4j.mq.nats;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import io.nats.client.*;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * NATS JetStream 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>NATS 无原生 broker-side tag filter（仅 subject 通配），应用层 {@link TagMatcher} tag 过滤保留。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : NatsMQClient ###")
public class NatsMQClient implements MQClient {

    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
    private final NatsProperties properties;
    /**
     * 用于延迟创建并复用代理连接的引用。
     */
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();
    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
    private final MQStartupStatus startupStatus = new MQStartupStatus("nats");

    /**
     * 双构造 1：注入已初始化的原生 NATS {@link Connection}（用于 runtime 集成自动注入）。
     *
     * @param connection 原生 NATS 连接
     */
    public NatsMQClient(Connection connection) {
        this.properties = new NatsProperties();
        if (Objects.nonNull(connection)) {
            this.connectionRef.set(connection);
        }
    }

    /**
     * 双构造 2：传入配置，{@link #connection()} 时 lazy 构造原生 NATS 连接。
     *
     * @param properties NATS 配置
     */
    public NatsMQClient(NatsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 等待 NATS 连接中的待处理操作排空，并处理原生客户端抛出的异常。
     *
     * @param connection 待释放或等待完成的原生连接
     */
    private static void drainConnection(Connection connection) {
        try {
            connection.drain(Duration.ofSeconds(5)).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Drain NATS connection failed", exception);
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Close NATS connection interrupted", exception);
        }
    }

    /**
     * 从 DDD4J 事件组装 NATS 传输侧消息头。
     *
     * @param event 当前发布或待补齐元数据的 DDD4J 消息事件
     * @return 发送给 NATS 的头字段集合
     */
    static Headers messageHeaders(MQEvent event) {
        Headers headers = new Headers();
        for (Map.Entry<String, String> header : event.headerMap().entrySet()) {
            headers.put(header.getKey(), header.getValue());
        }
        if (StrKit.isNotEmpty(event.getTag())) {
            headers.put(MessageHeaders.HEADER_DESTINATION_TAG, event.getTag());
        }
        return headers;
    }

    /**
     * 优先从原生消息的标准头读取消息标识，缺少时回退到兼容头。
     *
     * @param headers 消息随协议附带的头字段
     * @return 用于后续消费确认的业务消息标识，头均缺失时为空
     */
    static String messageId(Headers headers) {
        if (Objects.isNull(headers)) {
            return null;
        }
        String messageId = headers.getFirst(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : headers.getFirst(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

    // ========================= 生产者 =========================

    /**
     * 在成功取到原生业务标识时将其恢复到事件。
     *
     * @param event     当前发布或待补齐元数据的 DDD4J 消息事件
     * @param messageId 从原生代理消息解析出的标识
     */
    private static void restoreMessageId(MQEvent event, String messageId) {
        if (StrKit.isNotEmpty(messageId)) {
            event.setMsgId(messageId);
        }
    }

    /**
     * 将原生消息的关联标识和因果标识恢复到领域消息。
     *
     * @param event   当前发布或待补齐元数据的 DDD4J 消息事件
     * @param headers 消息随协议附带的头字段
     */
    private static void restoreCausality(MQEvent event, Headers headers) {
        if (Objects.isNull(headers)) {
            return;
        }
        String correlationId = headers.getFirst(MQDeliveryHeaders.CORRELATION_ID);
        if (StrKit.isNotEmpty(correlationId)) {
            event.setCorrelationId(correlationId);
        }
        String causationId = headers.getFirst(MQDeliveryHeaders.CAUSATION_ID);
        if (StrKit.isNotEmpty(causationId)) {
            event.setCausationId(causationId);
        }
    }

    // ========================= 消费者 =========================

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"nats"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "nats";
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

    // ========================= 连接管理 =========================

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
     * NATS 无原生 broker-side tag selector，仅 subject 通配 → 强制应用层 {@link TagMatcher} 过滤。
     *
     * @return 当前代理是否支持按标签过滤
     */
    @Override
    public boolean supportsBrokerTagFilter() {
        return false;
    }

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>此实现调用 <code>connection()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        Connection conn = connection();
        return event -> {
            String payload = serialization().serialize(event);
            String subject = resolveTopic(namespace(event, mqProperties), event.getTopic(), null, concat(event));
            try {
                byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                Headers headers = messageHeaders(event);
                // JetStream 优先，失败回落 core NATS（如未启用 JetStream）
                try {
                    JetStream jetStream = conn.jetStream();
                    jetStream.publish(subject, headers, body);
                } catch (IOException | JetStreamApiException ex) {
                    if (properties.isJetStreamRequired()) {
                        throw ex;
                    }
                    conn.publish(subject, headers, body);
                }
            } catch (Exception ex) {
                log.error("Publish NATS [{}]: {} failed!", subject, payload, ex);
                throw new IllegalStateException("Publish NATS event failed", ex);
            }
            log.info("Publish MQ [{}]: {}", subject, payload);
        };
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>connection()</code>。</p>
     *
     * @param listener     监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        Connection conn = connection();
        String subject = resolveTopic(namespace(listener.getNamespace(), mqProperties),
                listener.getTopic(), null, defaultConcat());
        int checkpoint = lifecycle.checkpoint();
        try {
            JetStream jetStream = conn.jetStream();
            Dispatcher dispatcher = conn.createDispatcher(msg -> {
            });
            lifecycle.register("nats-dispatcher-" + subject, () -> conn.closeDispatcher(dispatcher));
            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .durable(listener.getGroup())
                    .build();
            io.nats.client.Subscription subscription =
                    jetStream.subscribe(subject, dispatcher, msg -> onMessage(msg, listener), false, options);
            // dispatcher 所属订阅禁止直接 unsubscribe（jnats 会抛 IllegalStateException），必须经 dispatcher 退订
            lifecycle.register("nats-subscription-" + subject, () -> dispatcher.unsubscribe(subscription));
            log.info("Registered NATS JetStream listener: subject={}, durable={}", subject, listener.getGroup());
        } catch (Exception ex) {
            lifecycle.rollback(checkpoint);
            if (properties.isJetStreamRequired()) {
                throw new IllegalStateException("NATS JetStream subscription is required: " + subject, ex);
            }
            log.warn("JetStream subscribe failed for subject={}, falling back to core NATS: {}",
                    subject, ex.getMessage());
            Dispatcher dispatcher = conn.createDispatcher(msg -> onMessage(msg, listener));
            dispatcher.subscribe(subject);
            lifecycle.register("nats-dispatcher-" + subject, () -> conn.closeDispatcher(dispatcher));
            log.info("Registered NATS core listener: subject={}", subject);
        }
        return true;
    }

    /**
     * 解码 NATS 消息、恢复消息元数据并转交当前监听器。
     *
     * @param natsMessage 由 NATS 原生订阅回调交付的消息
     * @param listener    当前代理订阅对应的 DDD4J 消息监听器
     */
    private void onMessage(Message natsMessage, MQListener listener) {
        try {
            String payload = new String(natsMessage.getData(), StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(defaultConcat()));
                return;
            }
            restoreMessageId(event, messageId(natsMessage.getHeaders()));
            restoreCausality(event, natsMessage.getHeaders());
            String tag = event.getTag();
            Headers headers = natsMessage.getHeaders();
            if (Objects.nonNull(headers)) {
                String nativeTag = headers.getFirst(MessageHeaders.HEADER_DESTINATION_TAG);
                if (StrKit.isNotEmpty(nativeTag)) {
                    tag = nativeTag;
                    event.setTag(nativeTag);
                }
            }
            NatsAcknowledgment ack = new NatsAcknowledgment(natsMessage);
            if (!TagMatcher.match(tag, listener.getTags())) {
                if (Objects.nonNull(natsMessage.metaData())) {
                    ack.ackSingle();
                }
                return;
            }
            consume(listener, event, ack);
            if (!ack.isAcknowledged()) {
                ack.ackSingle();
            }
        } catch (Throwable ex) {
            log.error("NATS consumer failed: subject={}", natsMessage.getSubject(), ex);
            if (Objects.nonNull(natsMessage.metaData())) {
                try {
                    natsMessage.nak();
                } catch (Exception nakEx) {
                    log.warn("Failed to nak NATS message after error", nakEx);
                }
            }
        }
    }

    /**
     * 取得当前适配器使用的原生连接或客户端，缺少实例时建立连接。
     *
     * @return 供发布或消费操作使用的原生连接或客户端
     */
    private synchronized Connection connection() {
        Connection c = connectionRef.get();
        if (Objects.isNull(c)) {
            c = properties.connect();
            connectionRef.set(c);
            Connection ownedConnection = c;
            lifecycle.register("nats-connection-close", () -> closeConnection(ownedConnection));
            lifecycle.register("nats-connection-drain", () -> drainConnection(ownedConnection));
        }
        return c;
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
