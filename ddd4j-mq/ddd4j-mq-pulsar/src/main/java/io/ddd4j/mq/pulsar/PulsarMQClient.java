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
package io.ddd4j.mq.pulsar;
import java.util.ArrayList;

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
import org.apache.pulsar.client.api.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Apache Pulsar 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : PulsarMQClient ###")
public class PulsarMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final PulsarProperties properties;
    /** \u5f53\u524d\u9002\u914d\u5668\u5df2\u5efa\u7acb\u7684\u539f\u751f\u6d88\u8d39\u8005\u3002 */
    private final List<org.apache.pulsar.client.api.Consumer<?>> consumers = new CopyOnWriteArrayList<>();
    /** \u6309\u4e3b\u9898\u7ba1\u7406\u7684 Pulsar \u539f\u751f\u751f\u4ea7\u8005\u3002 */
    private final ConcurrentMap<String, Producer<byte[]>> producers = new ConcurrentHashMap<>();
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("pulsar");
    /** \u5efa\u7acb Pulsar \u751f\u4ea7\u8005\u548c\u6d88\u8d39\u8005\u7684\u539f\u751f\u5ba2\u6237\u7aef\u3002 */
    private PulsarClient client;

    /**
     * 构造 1：传入配置，{@link #initProducer} 中通过 PulsarClient.builder().build() 创建原生客户端。
     *
     * @param properties Pulsar 配置
     */
    public PulsarMQClient(PulsarProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.client = null;
    }

    /**
     * 构造 2：注入已初始化的原生 Pulsar 客户端（用于 runtime 集成自动注入）。
     * 物理 topic 仍来自 {@link PulsarProperties#physicalTopic}，因此同时接受 properties。
     *
     * @param client     原生 PulsarClient
     * @param properties Pulsar 配置（topic 命名空间、订阅名等）
     */
    public PulsarMQClient(org.apache.pulsar.client.api.PulsarClient client, PulsarProperties properties) {
        this.client = client;
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * \u5c06\u539f\u751f Pulsar \u6d88\u606f\u6807\u8bc6\u8f6c\u6362\u4e3a\u6587\u672c\u3002
     * @param id \u539f\u751f\u4ee3\u7406\u63d0\u4f9b\u7684\u6d88\u606f\u6807\u8bc6
     * @return \u6d88\u606f\u6807\u8bc6\u6587\u672c\uff0c\u539f\u751f\u503c\u4e3a\u7a7a\u65f6\u8fd4\u56de\u7a7a
     */
    private static String messageIdString(MessageId id) {
        return Objects.isNull(id) ? null : id.toString();
    }

    /**
     * \u4f18\u5148\u8bfb\u53d6 ddd4j \u6807\u51c6\u6d88\u606f ID\uff0c\u517c\u5bb9\u65e7\u952e\uff0c\u6700\u540e\u56de\u9000\u5230 Pulsar \u6295\u9012 ID\u3002
     *
     * @param message \u6536\u5230\u7684\u539f\u751f Pulsar \u6d88\u606f
     * @return \u7528\u4e8e\u6d88\u8d39\u786e\u8ba4\u6216\u65e5\u5fd7\u8bb0\u5f55\u7684\u6d88\u606f\u6807\u8bc6
     */
    static String messageId(Message<?> message) {
        String messageId = message.getProperty(MessageHeaders.HEADER_MESSAGE_ID);
        if (Objects.isNull(messageId)) {
            messageId = message.getProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
        }
        return Objects.nonNull(messageId) ? messageId : messageIdString(message.getMessageId());
    }

    /**
     * \u5173\u95ed\u539f\u751f\u6d88\u8d39\u8005\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param consumer \u672c\u6b21\u64cd\u4f5c\u6d89\u53ca\u7684\u539f\u751f\u6d88\u8d39\u8005
     */
    private static void closeConsumer(org.apache.pulsar.client.api.Consumer<?> consumer) {
        try {
            consumer.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Close Pulsar consumer failed", exception);
        }
    }

    /**
     * \u5173\u95ed\u539f\u751f\u751f\u4ea7\u8005\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param producer \u672c\u6b21\u64cd\u4f5c\u6d89\u53ca\u7684\u539f\u751f\u751f\u4ea7\u8005
     */
    private static void closeProducer(Producer<byte[]> producer) {
        try {
            producer.flush();
            producer.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Close Pulsar producer failed", exception);
        }
    }

    /**
     * \u5173\u95ed Pulsar \u5ba2\u6237\u7aef\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param client \u5efa\u7acb Pulsar \u751f\u4ea7\u8005\u548c\u6d88\u8d39\u8005\u7684\u539f\u751f\u5ba2\u6237\u7aef
     */
    private static void closePulsarClient(PulsarClient client) {
        try {
            client.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Close Pulsar client failed", exception);
        }
    }

    // ========================= 生产者 =========================

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"pulsar"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "pulsar";
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

    // ========================= 消费者 =========================

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
     * \u4eff\u7167 {@code KafkaMQClient}\uff1a\u6839\u636e {@link io.ddd4j.mq.MQProperties#getPartitionKeyStrategy()}
     *  \u8ba1\u7b97 Pulsar \u6d88\u606f routing key\uff0c\u4fdd\u8bc1\u540c key \u8fdb\u540c partition\uff08\u987a\u5e8f\u6295\u9012\uff09\u3002
     *
     *  <p>Pulsar \u5728 {@code Key_Shared} \u8ba2\u9605\u4e0b\u6309\u6b64 key \u505a\u54c8\u5e0c\u8def\u7531\uff1b\u751f\u4ea7\u7aef\u901a\u8fc7
     *  {@code TypedMessageBuilder.key(...)} \u5199\u5165\u3002
     *
     *  <ul>
     *    <li>NONE \u2192 {@code null}\uff08\u8f6e\u8be2\uff0c\u65e0\u987a\u5e8f\u4fdd\u8bc1\uff09</li>
     *    <li>TAG \u2192 event.tag</li>
     *    <li>TENANT \u2192 event.tenantId</li>
     *    <li>TAG_TENANT \u2192 \u590d\u7528\u7236\u7c7b {@code tag|tenant} \u590d\u5408 key\uff08\u9ed8\u8ba4\uff09</li>
     *    <li>CUSTOM \u2192 \u7236\u7c7b\u9ed8\u8ba4\uff08\u5360\u4f4d\uff1a\u5b50\u7c7b\u81ea\u884c\u8986\u5199\uff09</li>
     *  </ul>
     *
     * @param event \u51b3\u5b9a\u6d88\u606f\u8def\u7531\u7684\u53d1\u5e03\u4e8b\u4ef6
     * @return \u7528\u4e8e Pulsar \u6d88\u606f\u8def\u7531\u7684\u952e
     */
    @Override
    public String partitionKey(MQEvent event) {
        if (Objects.isNull(properties)) {
            return MQClient.super.partitionKey(event);  // 双构造 2：注入 client 时走父类默认
        }
        return switch (properties.getPartitionKeyStrategy()) {
            case NONE -> null;
            case TAG -> Objects.nonNull(event) ? event.getTag() : null;
            case TENANT -> Objects.nonNull(event) ? event.getTenantId() : null;
            case TAG_TENANT -> MQClient.super.partitionKey(event);
            case CUSTOM -> MQClient.super.partitionKey(event);  // 占位：子类应自己覆写
        };
    }

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>此实现调用 <code>properties.client()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            if (Objects.isNull(this.client)) {
                this.client = properties.client();
                PulsarClient ownedClient = this.client;
                lifecycle.register("pulsar-client", () -> closePulsarClient(ownedClient));
            }
            return event -> {
                try {
                    String topic = event.getTopic();
                    String tag = event.getTag();
                    // 消费者按逻辑 topic 订阅；tag 由消息属性在消费端过滤。
                    String physical = properties.physicalTopic(topic, null);
                    Producer<byte[]> p = producer(physical);
                    TypedMessageBuilder<byte[]> builder = p.newMessage()
                            .value(serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8))
                            .property(MessageHeaders.HEADER_DESTINATION_TOPIC, topic);
                    String routingKey = partitionKey(event);
                    if (Objects.nonNull(routingKey)) {
                        builder.key(routingKey); // Pulsar partition routing：同 key 进同 partition
                    }
                    for (Map.Entry<String, String> header : event.headerMap().entrySet()) {
                        builder.property(header.getKey(), header.getValue());
                    }
                    if (Objects.nonNull(event.getTenantId())) {
                        builder.property(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
                    }
                    if (Objects.nonNull(tag)) {
                        builder.property(tagHeaderKey(), tag);
                    }
                    // Outbox 只有在 Pulsar broker 确认后才能标记发布成功。
                    // 用 properties.operationTimeoutMs 作 wait 超时，避免 broker 卡顿时无限阻塞。
                    builder.sendAsync().get(properties.getOperationTimeoutMs(), TimeUnit.MILLISECONDS);
                    logger().info("Publish MQ [{}]: {}", physical, serialization().serialize(event));
                } catch (Exception ex) {
                    throw new IllegalStateException("Publish Pulsar event failed", ex);
                }
            };
        } catch (Exception ex) {
            throw new IllegalStateException("Init Pulsar producer failed", ex);
        }
    }

    // ========================= 关闭 =========================

    /**
     * \u67e5\u627e\u6307\u5b9a\u4e3b\u9898\u7684\u539f\u751f Pulsar \u751f\u4ea7\u8005\uff0c\u7f3a\u5c11\u65f6\u521b\u5efa\u3002
     * @param topic \u8981\u53d1\u5e03\u3001\u8ba2\u9605\u6216\u53d6\u6d88\u8ba2\u9605\u7684\u4ee3\u7406\u4e3b\u9898
     * @return \u53ef\u5411\u6307\u5b9a\u4e3b\u9898\u53d1\u5e03\u7684\u751f\u4ea7\u8005
     * @throws Exception \u539f\u751f\u4ee3\u7406\u6216\u5ba2\u6237\u7aef\u64cd\u4f5c\u5931\u8d25
     */
    private Producer<byte[]> producer(String topic) throws Exception {
        return producers.computeIfAbsent(topic, t -> {
            try {
                Producer<byte[]> producer = client.newProducer(Schema.BYTES)
                        .topic(t)
                        .batchingMaxPublishDelay(10, TimeUnit.MILLISECONDS)
                        .create();
                lifecycle.register("pulsar-producer-" + t, () -> closeProducer(producer));
                return producer;
            } catch (Exception ex) {
                throw new IllegalStateException("Create Pulsar producer failed: " + t, ex);
            }
        });
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>listener.getTopic()</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        Objects.requireNonNull(listener, "listener");
        // Pulsar 物理 topic 用 tenant/namespace/topic 拼接，不含 tag（tag 作为 property 写入并应用层过滤）
        String topic = properties.physicalTopic(
                Objects.nonNull(listener.getTopic()) ? listener.getTopic() : "ddd4j.default.topic", null);
        String subscriptionName = properties.getSubscriptionName() + "-" + listener.getRouteExpression(this.defaultConcat());
        org.apache.pulsar.client.api.Consumer<byte[]> consumer = client.newConsumer(Schema.BYTES)
                .topic(topic)
                .subscriptionName(subscriptionName)
                .subscriptionType(SubscriptionType.valueOf(properties.getSubscriptionType()))
                .negativeAckRedeliveryDelay(properties.getNegativeAckRedeliveryDelayMs(), TimeUnit.MILLISECONDS)
                .messageListener((c, msg) -> {
                    try {
                        String tag = msg.getProperty(tagHeaderKey());
                        if (!TagMatcher.match(tag, listener.getTags())) {
                            ((org.apache.pulsar.client.api.Consumer) c).acknowledge(msg);
                            return;
                        }
                        String messageId = messageId(msg);
                        String payload = new String(msg.getValue(), StandardCharsets.UTF_8);
                        MQEvent event = serialization().deserialize(payload, listener.payloadType());
                        if (Objects.isNull(event)) {
                            logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                            ((org.apache.pulsar.client.api.Consumer) c).acknowledge(msg);
                            return;
                        }
                        if (Objects.nonNull(tag)) {
                            event.setTag(tag);
                        }
                        if (Objects.nonNull(messageId)) {
                            event.setMsgId(messageId);
                        }
                        String correlationId = msg.getProperty(MQDeliveryHeaders.CORRELATION_ID);
                        if (Objects.nonNull(correlationId)) {
                            event.setCorrelationId(correlationId);
                        }
                        String causationId = msg.getProperty(MQDeliveryHeaders.CAUSATION_ID);
                        if (Objects.nonNull(causationId)) {
                            event.setCausationId(causationId);
                        }
                        PulsarAcknowledgment ack = new PulsarAcknowledgment(c, msg, messageId, event.getCorrelationId());
                        try {
                            consume(listener, event, ack);
                            if (!ack.isAcknowledged()) {
                                ack.ackSingle();
                            }
                        } catch (Throwable ex) {
                            logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                            try {
                                ((org.apache.pulsar.client.api.Consumer) c).negativeAcknowledge(msg);
                            } catch (Exception ignore) {
                            }
                        }
                    } catch (Throwable ex) {
                        logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                        try {
                            ((org.apache.pulsar.client.api.Consumer) c).negativeAcknowledge(msg);
                        } catch (Exception ignore) {
                        }
                    }
                })
                .subscribe();
        consumers.add(consumer);
        lifecycle.register("pulsar-consumer-" + subscriptionName, () -> closeConsumer(consumer));
        return true;
    }

    /** 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。 */
    @Override
    public void start() {
        // Pulsar consumer 在 .subscribe() 时已启动
    }

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
    @Override
    public void close() {
        try {
            lifecycle.close();
        } finally {
            consumers.clear();
            producers.clear();
            startupStatus.stopped();
        }
    }
}
