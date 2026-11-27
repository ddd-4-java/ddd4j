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
import java.util.List;
import java.util.Map;
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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : OnsMQClient ###")
public class OnsMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final OnsProperties properties;
    /** \u5f53\u524d\u9002\u914d\u5668\u5df2\u5efa\u7acb\u7684\u539f\u751f\u6d88\u8d39\u8005\u3002 */
    private final List<com.aliyun.openservices.ons.api.Consumer> consumers = new CopyOnWriteArrayList<>();
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("ons");
    /** 保存 <code>producer</code> 对应的 <code>Producer</code> 数据。 */
    private volatile Producer producer;
    /** 保存 <code>producer Started</code> 对应的 <code>boolean</code> 数据。 */
    private volatile boolean producerStarted;
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
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

    /**
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(Message message) {
        String messageId = message.getUserProperties(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : message.getUserProperties(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

    /**
     * \u5c06 DDD4J \u6d88\u606f\u5143\u6570\u636e\u5199\u5165 ONS \u539f\u751f\u6d88\u606f\u7528\u6237\u5c5e\u6027\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @param event \u5f53\u524d\u53d1\u5e03\u6216\u5f85\u8865\u9f50\u5143\u6570\u636e\u7684 DDD4J \u6d88\u606f\u4e8b\u4ef6
     */
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
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"ons"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "ons";
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

    // ========================= 生产者 =========================

    /**
     * 返回消息客户端当前记录的启动状态。
     *
     * @return 当前消息适配器记录的启动状态
     */
    @Override
    public MQStartupStatus startupStatus() {
        return startupStatus;
    }

    // ========================= 消费者 =========================

    /**
     * \u7528\u6307\u5b9a\u4f1a\u8bdd\u5c5e\u6027\u521b\u5efa ONS \u53d1\u5e03\u5ba2\u6237\u7aef\u3002
     * @param sessionProperties \u5f53\u524d ONS \u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u5c5e\u6027
     * @return \u5c1a\u5f85\u542f\u52a8\u7684\u539f\u751f\u751f\u4ea7\u8005
     */
    Producer createProducer(Properties sessionProperties) {
        return ONSFactory.createProducer(sessionProperties);
    }

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 此实现调用 <code>createProducer(properties.sessionProperties(properties.getProducerId()))</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
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

    /**
     * \u7528\u6307\u5b9a\u4f1a\u8bdd\u5c5e\u6027\u521b\u5efa ONS \u6d88\u8d39\u5ba2\u6237\u7aef\u3002
     * @param sessionProperties \u5f53\u524d ONS \u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u5c5e\u6027
     * @return \u5c1a\u5f85\u8ba2\u9605\u7684\u539f\u751f\u6d88\u8d39\u8005
     */
    com.aliyun.openservices.ons.api.Consumer createConsumer(Properties sessionProperties) {
        return ONSFactory.createConsumer(sessionProperties);
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 此实现调用 <code>StrKit.hasText(listener.getGroup())</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
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

    // ========================= 关闭 =========================

    /** 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。 */
    @Override
    public void start() {
        // ONS producer/consumer 在各自创建时已启动；此方法留作 future 扩展。
    }

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
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
