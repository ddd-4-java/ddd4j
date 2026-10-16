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
package io.ddd4j.mq.ons;
import java.util.ArrayList;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.Producer;
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
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 阿里云 ONS 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>ONS 是阿里云 RocketMQ，提供原生 subscription 表达式 tag 过滤。
 *
 * @author ddd4j
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : OnsMQClient ###")
public class OnsMQClient implements MQClient {

    private final OnsProperties properties;
    private final List<com.aliyun.openservices.ons.api.Consumer> consumers = new CopyOnWriteArrayList<>();
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    private final MQStartupStatus startupStatus = new MQStartupStatus("ons");
    private volatile Producer producer;
    private volatile boolean producerStarted;
    private volatile boolean closed;

    /**
     * 构造 1：传入配置，{@link #initProducer}/{@link #initConsumer} 中通过 ONSFactory 创建原生客户端。
     *
     * @param properties ONS 配置
     */
    public OnsMQClient(OnsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 构造 2：注入已初始化的原生 ONS {@link Producer}（用于 runtime 集成自动注入）。
     * 注：ONS 消费者侧仍需 {@link OnsProperties} 通过 ONSFactory 创建，因此构造 2 同时接受
     * {@link OnsProperties}（供消费者侧使用）。
     *
     * @param producer   原生 ONS producer（运行时构造预创建）
     * @param properties ONS 配置（消费者侧使用）
     */
    public OnsMQClient(Producer producer, OnsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.producer = producer;
    }

    static String messageId(Message message) {
        String messageId = message.getUserProperties(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : message.getUserProperties(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

    static void applyNativeHeaders(Message message, MQEvent event) {
        String tenantId = message.getUserProperties(MessageHeaders.HEADER_TENANT_ID);
        if (StrKit.isNotEmpty(tenantId)) {
            event.setTenantId(tenantId);
        }
        String stableId = messageId(message);
        if (StrKit.isNotEmpty(stableId)) {
            event.setMsgId(stableId);
        } else if (StrKit.isEmpty(event.getMsgId()) && StrKit.isNotEmpty(message.getMsgID())) {
            event.setMsgId(message.getMsgID());
        }
        String correlationId = message.getUserProperties(MQDeliveryHeaders.CORRELATION_ID);
        if (StrKit.isNotEmpty(correlationId)) {
            event.setCorrelationId(correlationId);
        }
        String causationId = message.getUserProperties(MQDeliveryHeaders.CAUSATION_ID);
        if (StrKit.isNotEmpty(causationId)) {
            event.setCausationId(causationId);
        }
    }

    /**
     * impl() 方法。
     * @return 结果字符串。
     */
    @Override
    public String impl() {
        return "ons";
    }

    /**
     * lifecycle() 方法。
     * @return 方法执行结果。
     */
    @Override
    public MQClientLifecycle lifecycle() {
        return lifecycle;
    }

    // ========================= 生产者 =========================

    /**
     * startupStatus() 方法。
     * @return 方法执行结果。
     */
    @Override
    public MQStartupStatus startupStatus() {
        return startupStatus;
    }

    // ========================= 消费者 =========================

    Producer createProducer(Properties sessionProperties) {
        return ONSFactory.createProducer(sessionProperties);
    }

    @Override
    public synchronized Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        if (closed) {
            throw new IllegalStateException("ONS client is closed");
        }
        try {
            boolean owned = Objects.isNull(producer);
            Producer p = owned ? createProducer(properties.sessionProperties(properties.getProducerId())) : producer;
            if (!producerStarted) {
                try {
                    p.start();
                    if (owned) {
                        lifecycle.register("ons-producer", p::shutdown);
                    }
                } catch (RuntimeException | Error exception) {
                    if (owned) {
                        try {
                            p.shutdown();
                        } catch (RuntimeException | Error shutdownFailure) {
                            exception.addSuppressed(shutdownFailure);
                        }
                    }
                    throw exception;
                }
                this.producer = p;
                this.producerStarted = true;
            }
            return event -> {
                if (closed) {
                    throw new IllegalStateException("ONS client is closed");
                }
                try {
                    String topic = resolveTopic(event, mqProperties);
                    String tag = event.getTag();
                    Message msg = new Message(topic, tag, event.getMsgId(),
                            serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8));
                    msg.setKey(event.getMsgId());
                    if (Objects.nonNull(event.getTenantId())) {
                        msg.putUserProperties(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
                    }
                    for (Map.Entry<String, String> header : event.headerMap().entrySet()) {
                        msg.putUserProperties(header.getKey(), header.getValue());
                    }
                    p.send(msg);
                    logger().info("Publish MQ [{}]: {}", topic, serialization().serialize(event));
                } catch (Exception ex) {
                    throw new IllegalStateException("Publish ONS event failed", ex);
                }
            };
        } catch (Exception ex) {
            throw new IllegalStateException("Init ONS producer failed", ex);
        }
    }

    // ========================= 消费者 =========================

    /**
     * initConsumer() 方法。
     * @param listener 消息监听器。
     * @param mqProperties MQ 连接属性。
     * @return 判断结果。
     * @throws Exception 执行过程中出现异常时抛出。
     */
    com.aliyun.openservices.ons.api.Consumer createConsumer(Properties sessionProperties) {
        return ONSFactory.createConsumer(sessionProperties);
    }

    @Override
    public synchronized boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        if (closed) {
            throw new IllegalStateException("ONS client is closed");
        }
        String group = StrKit.hasText(listener.getGroup()) ? listener.getGroup() : properties.getConsumerId();
        if (!StrKit.hasText(group)) {
            throw new IllegalStateException("OnsClient requires consumerId or @MQEventListener(group=...)");
        }
        String topic = StrKit.hasText(listener.getTopic()) ? listener.getTopic() : properties.getTopic();
        if (!StrKit.hasText(topic)) {
            throw new IllegalStateException("OnsClient requires topic");
        }
        // ONS 支持 subscription 表达式原生 tag 过滤
        String tagExpression = properties.subscriptionExpression(
                TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null));
        com.aliyun.openservices.ons.api.Consumer consumer = createConsumer(properties.sessionProperties(group));
        try {
            consumer.subscribe(topic, tagExpression, (msg, ctx) -> {
                try {
                    if (!TagMatcher.match(msg.getTag(), listener.getTags())) {
                        return com.aliyun.openservices.ons.api.Action.CommitMessage;
                    }
                    String payload = new String(msg.getBody(), StandardCharsets.UTF_8);
                    MQEvent event = serialization().deserialize(payload, listener.payloadType());
                    if (Objects.isNull(event)) {
                        logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                        return com.aliyun.openservices.ons.api.Action.CommitMessage;
                    }
                    if (Objects.nonNull(msg.getTag())) {
                        event.setTag(msg.getTag());
                    }
                    applyNativeHeaders(msg, event);
                    OnsAcknowledgment ack = new OnsAcknowledgment(ctx, msg);
                    try {
                        consume(listener, event, ack);
                        if (!ack.isAcknowledged()) {
                            ack.ackSingle();
                        }
                    } catch (Throwable ex) {
                        logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                        return com.aliyun.openservices.ons.api.Action.ReconsumeLater;
                    }
                    return ack.action();
                } catch (Throwable ex) {
                    logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                    return com.aliyun.openservices.ons.api.Action.ReconsumeLater;
                }
            });
            consumer.start();
        } catch (RuntimeException | Error exception) {
            try {
                consumer.shutdown();
            } catch (RuntimeException | Error shutdownFailure) {
                exception.addSuppressed(shutdownFailure);
            }
            throw exception;
        }
        consumers.add(consumer);
        lifecycle.register("ons-consumer-" + group, consumer::shutdown);
        return true;
    }

    /**
     * 启动。
     */
    @Override
    public void start() {
        // ONS producer/consumer 在各自创建时已启动；此方法留作 future 扩展。
    }

    // ========================= 关闭 =========================

    /**
     * 关闭并释放资源。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            lifecycle.close();
        } finally {
            consumers.clear();
            producerStarted = false;
            startupStatus.stopped();
        }
    }
}
