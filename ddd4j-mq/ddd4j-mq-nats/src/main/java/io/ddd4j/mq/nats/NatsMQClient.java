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

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final NatsProperties properties;
    /** \u7528\u4e8e\u5ef6\u8fdf\u521b\u5efa\u5e76\u590d\u7528\u4ee3\u7406\u8fde\u63a5\u7684\u5f15\u7528\u3002 */
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
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
     * \u7b49\u5f85 NATS \u8fde\u63a5\u4e2d\u7684\u5f85\u5904\u7406\u64cd\u4f5c\u6392\u7a7a\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param connection \u5f85\u91ca\u653e\u6216\u7b49\u5f85\u5b8c\u6210\u7684\u539f\u751f\u8fde\u63a5
     */
    private static void drainConnection(Connection connection) {
        try {
            connection.drain(Duration.ofSeconds(5)).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Drain NATS connection failed", exception);
        }
    }

    /**
     * \u5173\u95ed\u539f\u751f\u4ee3\u7406\u8fde\u63a5\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param connection \u5f85\u91ca\u653e\u6216\u7b49\u5f85\u5b8c\u6210\u7684\u539f\u751f\u8fde\u63a5
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
     * \u4ece DDD4J \u4e8b\u4ef6\u7ec4\u88c5 NATS \u4f20\u8f93\u4fa7\u6d88\u606f\u5934\u3002
     * @param event \u5f53\u524d\u53d1\u5e03\u6216\u5f85\u8865\u9f50\u5143\u6570\u636e\u7684 DDD4J \u6d88\u606f\u4e8b\u4ef6
     * @return \u53d1\u9001\u7ed9 NATS \u7684\u5934\u5b57\u6bb5\u96c6\u5408
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
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param headers \u6d88\u606f\u968f\u534f\u8bae\u9644\u5e26\u7684\u5934\u5b57\u6bb5
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
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
     * \u5728\u6210\u529f\u53d6\u5230\u539f\u751f\u4e1a\u52a1\u6807\u8bc6\u65f6\u5c06\u5176\u6062\u590d\u5230\u4e8b\u4ef6\u3002
     * @param event \u5f53\u524d\u53d1\u5e03\u6216\u5f85\u8865\u9f50\u5143\u6570\u636e\u7684 DDD4J \u6d88\u606f\u4e8b\u4ef6
     * @param messageId \u4ece\u539f\u751f\u4ee3\u7406\u6d88\u606f\u89e3\u6790\u51fa\u7684\u6807\u8bc6
     */
    private static void restoreMessageId(MQEvent event, String messageId) {
        if (StrKit.isNotEmpty(messageId)) {
            event.setMsgId(messageId);
        }
    }

    /**
     * \u5c06\u539f\u751f\u6d88\u606f\u7684\u5173\u8054\u6807\u8bc6\u548c\u56e0\u679c\u6807\u8bc6\u6062\u590d\u5230\u9886\u57df\u6d88\u606f\u3002
     * @param event \u5f53\u524d\u53d1\u5e03\u6216\u5f85\u8865\u9f50\u5143\u6570\u636e\u7684 DDD4J \u6d88\u606f\u4e8b\u4ef6
     * @param headers \u6d88\u606f\u968f\u534f\u8bae\u9644\u5e26\u7684\u5934\u5b57\u6bb5
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
     * NATS \u65e0\u539f\u751f broker-side tag selector\uff0c\u4ec5 subject \u901a\u914d \u2192 \u5f3a\u5236\u5e94\u7528\u5c42 {@link TagMatcher} \u8fc7\u6ee4\u3002
     *
     * @return \u5f53\u524d\u4ee3\u7406\u662f\u5426\u652f\u6301\u6309\u6807\u7b7e\u8fc7\u6ee4
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
     * @param listener 监听器定义
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
            // 必须经 dispatcher 退订：仅 subscription.unsubscribe() 不会把订阅从 dispatcher
            // 的订阅表移除，重订阅时会命中 "already subscribed" 或继续向旧订阅投递
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
     * \u89e3\u7801 NATS \u6d88\u606f\u3001\u6062\u590d\u6d88\u606f\u5143\u6570\u636e\u5e76\u8f6c\u4ea4\u5f53\u524d\u76d1\u542c\u5668\u3002
     * @param natsMessage \u7531 NATS \u539f\u751f\u8ba2\u9605\u56de\u8c03\u4ea4\u4ed8\u7684\u6d88\u606f
     * @param listener \u5f53\u524d\u4ee3\u7406\u8ba2\u9605\u5bf9\u5e94\u7684 DDD4J \u6d88\u606f\u76d1\u542c\u5668
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
     * \u53d6\u5f97\u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef\uff0c\u7f3a\u5c11\u5b9e\u4f8b\u65f6\u5efa\u7acb\u8fde\u63a5\u3002
     * @return \u4f9b\u53d1\u5e03\u6216\u6d88\u8d39\u64cd\u4f5c\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef
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
