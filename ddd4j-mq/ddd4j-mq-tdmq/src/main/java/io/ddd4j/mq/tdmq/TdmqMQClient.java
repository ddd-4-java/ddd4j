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
package io.ddd4j.mq.tdmq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.pulsar.PulsarMQClient;
import io.ddd4j.mq.pulsar.PulsarProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 腾讯云 TDMQ 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>配置 serviceUrl 时通过社区 Pulsar 客户端连接 TDMQ Pulsar 服务；业务侧仍可通过
 * {@link BrokerPublisher} / {@link BrokerSubscriber} 注入已有传输实现。
 * 保留旧 SPI 单抽象方法供现有调用方使用；元数据重载负责冻结的 broker header。
 * 旧发布器无法携带因果头时显式失败，不得把 transport message id 冒充业务稳定 ID。
 * 未配置 serviceUrl 且未注入传输时，提供"内存总线"实现，仅供本地联调/测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : TdmqMQClient ###")
public class TdmqMQClient implements MQClient {

    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
    private final TdmqProperties properties;
    /**
     * 当前 TDMQ 客户端维护的消费订阅。
     */
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    /**
     * 按消息主题记录当前订阅及其监听器。
     */
    private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers =
            new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
    private final MQStartupStatus startupStatus = new MQStartupStatus("tdmq");
    /**
     * 用于原生连接模式的 Pulsar 客户端适配器。
     */
    private final PulsarMQClient nativePulsar;
    /**
     * 测试或自定义代理的消息发布入口。
     */
    private BrokerPublisher brokerPublisher;
    /**
     * 测试或自定义代理的消息订阅入口。
     */
    private BrokerSubscriber brokerSubscriber;
    /**
     * 当前实例是否已关闭。
     */
    private volatile boolean closed;

    /**
     * 构造 1：仅 properties（业务可在 initProducer/initConsumer 之前注入 BrokerPublisher/BrokerSubscriber）。
     *
     * @param properties TDMQ 连接配置
     */
    public TdmqMQClient(TdmqProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nativePulsar = StrKit.isBlank(properties.getServiceUrl()) ? null : new PulsarMQClient(
                pulsarProperties(properties)) {
            @Override
            public void consume(MQListener listener, MQEvent event, Acknowledgment ack) throws Throwable {
                TdmqMQClient.this.consume(listener, event, ack);
            }
        };
    }

    /**
     * 构造 2：注入外部 BrokerPublisher / BrokerSubscriber（runtime 集成时由业务 SDK 包装传入）。
     *
     * @param publisher  业务侧发布器
     * @param subscriber 业务侧订阅器
     * @param properties TDMQ 连接配置
     */
    public TdmqMQClient(BrokerPublisher publisher, BrokerSubscriber subscriber, TdmqProperties properties) {
        this.brokerPublisher = publisher;
        this.brokerSubscriber = subscriber;
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nativePulsar = null;
    }

    /**
     * 将 TDMQ 配置映射成原生 Pulsar 适配器属性。
     *
     * @param source 用来生成 Pulsar 配置的 TDMQ 连接属性
     * @return 供 Pulsar 直接连接的配置
     */
    private static PulsarProperties pulsarProperties(TdmqProperties source) {
        PulsarProperties target = new PulsarProperties();
        target.setEnabled(source.isEnabled());
        target.setBroker("pulsar");
        target.setServiceUrl(source.getServiceUrl());
        target.setTenant(source.getTenant());
        target.setAuthToken(source.getAuthToken());
        target.setNamespace(source.getNamespace());
        target.setDefaultTopic(source.getDefaultTopic());
        target.setSerialization(source.getSerialization());
        target.setPersist(source.isPersist());
        target.setAutoAck(source.isAutoAck());
        target.setRetries(source.getRetries());
        target.setPartitionKeyStrategy(source.getPartitionKeyStrategy());
        target.setSubscriptionName(source.getDefaultGroup());
        return target;
    }

    /**
     * 检查发布和订阅是否都由原生 Pulsar 适配器负责。
     *
     * @return 仅使用原生适配器时返回 true
     */
    private boolean nativeMode() {
        return Objects.nonNull(nativePulsar) && Objects.isNull(brokerPublisher)
                && Objects.isNull(brokerSubscriber);
    }

    /**
     * 注入 TDMQ 业务侧发布器（如腾讯云 SDK 包装）。
     *
     * @param publisher 业务侧发布器
     */
    public void setBrokerPublisher(BrokerPublisher publisher) {
        this.brokerPublisher = publisher;
    }

    /**
     * 注入 TDMQ 业务侧订阅器（如腾讯云 SDK 包装）。
     *
     * @param subscriber 业务侧订阅器
     */
    public void setBrokerSubscriber(BrokerSubscriber subscriber) {
        this.brokerSubscriber = subscriber;
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"tdmq"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "tdmq";
    }

    /**
     * 返回管理连接、生产者和消费者资源的生命周期对象。
     *
     * <p>此实现调用 <code>nativeMode()</code>。</p>
     *
     * @return 当前消息适配器用于登记和关闭资源的生命周期对象
     */
    @Override
    public MQClientLifecycle lifecycle() {
        return nativeMode() ? nativePulsar.lifecycle() : lifecycle;
    }

    /**
     * 返回消息客户端当前记录的启动状态。
     *
     * <p>此实现调用 <code>nativeMode()</code>。</p>
     *
     * @return 当前消息适配器记录的启动状态
     */
    @Override
    public MQStartupStatus startupStatus() {
        return nativeMode() ? nativePulsar.startupStatus() : startupStatus;
    }

    // ========================= 生产者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 此实现调用 <code>nativeMode()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public synchronized Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        if (closed) {
            throw new IllegalStateException("TDMQ client is closed");
        }
        if (nativeMode()) {
            Consumer<MQEvent> publisher = nativePulsar.initProducer(mqProperties);
            return event -> {
                if (closed) {
                    throw new IllegalStateException("TDMQ client is closed");
                }
                publisher.accept(event);
            };
        }
        if (Objects.isNull(brokerPublisher)) {
            this.brokerPublisher = new InMemoryBrokerPublisher(topicSubscribers);
            log.warn("TdmqMQClient: no BrokerPublisher injected, falling back to in-memory broker (test only).");
        }
        return event -> {
            if (closed) {
                throw new IllegalStateException("TDMQ client is closed");
            }
            try {
                String topic = resolveTopic(event, mqProperties);
                String tag = event.getTag();
                String payload = serialization().serialize(event).toString();
                brokerPublisher.publish(topic, tag,
                        Objects.nonNull(event.getTenantId()) ? event.getTenantId() : "",
                        Objects.nonNull(event.getMsgId()) ? event.getMsgId() : "",
                        payload.getBytes(StandardCharsets.UTF_8), event.headerMap());
                logger().info("Publish MQ [{}]: {}", topic, payload);
            } catch (Exception ex) {
                throw new IllegalStateException("Publish TDMQ event failed", ex);
            }
        };
    }

    // ========================= 消费者 =========================

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 此实现调用 <code>properties.isAutoStartConsumers()</code>。</p>
     *
     * @param listener     监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 消费者完成注册时为真
     */
    @Override
    public synchronized boolean initConsumer(MQListener listener, MQProperties mqProperties) {
        if (closed) {
            throw new IllegalStateException("TDMQ client is closed");
        }
        if (!properties.isAutoStartConsumers()) {
            log.info("TDMQ listener registration skipped because autoStartConsumers=false");
            return false;
        }
        if (nativeMode()) {
            try {
                return nativePulsar.initConsumer(listener, mqProperties);
            } catch (Exception exception) {
                throw new IllegalStateException("Initialize TDMQ Pulsar consumer failed", exception);
            }
        }
        String topic = resolveTopic(listener, mqProperties);
        String tagExpression = listener.getTags();
        String group = StrKit.hasText(listener.getGroup()) ? listener.getGroup() : properties.getDefaultGroup();
        if (Objects.isNull(brokerSubscriber)) {
            this.brokerSubscriber = new InMemoryBrokerSubscriber(topicSubscribers);
            log.warn("TdmqMQClient: no BrokerSubscriber injected, falling back to in-memory broker (test only).");
        }
        Subscription subscription = brokerSubscriber.subscribe(topic, tagExpression, group,
                (messageId, correlationId, headers, payload, requeue) -> {
                    try {
                        String payloadText = Objects.isNull(payload) ? "" : new String(payload, StandardCharsets.UTF_8);
                        String tag = null;
                        int lastDot = topic.lastIndexOf('.');
                        if (lastDot >= 0) {
                            tag = topic.substring(lastDot + 1);
                        }
                        if (!io.ddd4j.mq.util.TagMatcher.match(tag, listener.getTags())) {
                            return;
                        }
                        MQEvent event = serialization().deserialize(payloadText, listener.payloadType());
                        Map<String, String> nativeHeaders = Objects.nonNull(headers) ? headers : Collections.emptyMap();
                        String stableId = nativeHeaders.get(MQDeliveryHeaders.MESSAGE_ID);
                        String nativeCorrelation = nativeHeaders.get(MQDeliveryHeaders.CORRELATION_ID);
                        if (Objects.nonNull(event)) {
                            if (StrKit.isNotEmpty(stableId)) {
                                event.setMsgId(stableId);
                            } else if (StrKit.isEmpty(event.getMsgId()) && StrKit.isNotEmpty(messageId)) {
                                event.setMsgId(messageId);
                            }
                            if (StrKit.isNotEmpty(nativeCorrelation)) {
                                event.setCorrelationId(nativeCorrelation);
                            }
                            String nativeCausation = nativeHeaders.get(MQDeliveryHeaders.CAUSATION_ID);
                            if (StrKit.isNotEmpty(nativeCausation)) {
                                event.setCausationId(nativeCausation);
                            }
                        }
                        String ackId = StrKit.isNotEmpty(stableId) ? stableId
                                : Objects.nonNull(event) && StrKit.isNotEmpty(event.getMsgId()) ? event.getMsgId() : messageId;
                        String ackCorrelation = StrKit.isNotEmpty(nativeCorrelation) ? nativeCorrelation
                                : Objects.nonNull(event) && StrKit.isNotEmpty(event.getCorrelationId())
                                ? event.getCorrelationId() : correlationId;
                        long deliveryTag = Objects.nonNull(correlationId) ? correlationId.hashCode() : 0L;
                        TdmqAcknowledgment ack = new TdmqAcknowledgment(ackId, ackCorrelation, deliveryTag, requeue);
                        if (Objects.isNull(event)) {
                            logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                            ack.ackSingle();
                            return;
                        }
                        try {
                            consume(listener, event, ack);
                            if (!ack.isAcknowledged()) {
                                ack.ackSingle();
                            }
                        } catch (Throwable ex) {
                            logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                            if (!ack.isAcknowledged()) {
                                ack.nack(properties.isRequeueOnError());
                            }
                        }
                    } catch (Throwable ex) {
                        logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                        requeue.accept(properties.isRequeueOnError());
                    }
                });
        subscriptions.add(subscription);
        lifecycle.register("tdmq-subscription-" + group, subscription::close);
        logger().info("Registered TDMQ listener: topic={}, tags={}, group={}", topic, tagExpression, group);
        return true;
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>此实现调用 <code>nativeMode()</code>。</p>
     */
    @Override
    public void start() {
        if (nativeMode()) {
            nativePulsar.start();
        }
    }

    // ========================= 关闭 =========================

    /**
     * 释放消息客户端拥有的运行资源，并在退出时登记组件已停止。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (nativeMode()) {
                nativePulsar.close();
            } else {
                lifecycle.close();
            }
        } finally {
            subscriptions.clear();
            topicSubscribers.clear();
            startupStatus.stopped();
        }
    }

    // ========================= 业务侧适配接口 =========================

    /**
     * TDMQ 业务侧发布器，由 {@link TdmqMQClient} 通过 {@link #setBrokerPublisher} 注入。
     */
    @FunctionalInterface
    public interface BrokerPublisher {

        /**
         * 发布消息。
         *
         * @param topic    物理 topic（含 namespace 前缀）
         * @param tag      tag，可为 null
         * @param tenantId 租户 ID
         * @param key      业务 Key
         * @param payload  序列化字节流
         */
        void publish(String topic, String tag, String tenantId, String key, byte[] payload);

        /**
         * 携带原生消息头发布；旧发布器遇到因果头时显式失败。
         *
         * @param topic    物理 topic
         * @param tag      tag
         * @param tenantId 租户 ID
         * @param key      业务 Key
         * @param payload  序列化内容
         * @param headers  broker 消息头
         */
        default void publish(String topic, String tag, String tenantId, String key,
                             byte[] payload, Map<String, String> headers) {
            Objects.requireNonNull(headers, "headers");
            if (headers.containsKey(MQDeliveryHeaders.CORRELATION_ID)
                    || headers.containsKey(MQDeliveryHeaders.CAUSATION_ID)) {
                throw new IllegalStateException("TDMQ BrokerPublisher does not support native causality headers");
            }
            publish(topic, tag, tenantId, key, payload);
        }
    }

    /**
     * TDMQ 业务侧订阅器，由 {@link TdmqMQClient} 通过 {@link #setBrokerSubscriber} 注入。
     */
    @FunctionalInterface
    public interface BrokerSubscriber {

        /**
         * 订阅。
         *
         * @param topic          物理 topic
         * @param tagExpression  tag 表达式
         * @param group          订阅组
         * @param messageHandler 消息处理器（messageId, correlationId, payload, ackCallback）
         * @return 订阅句柄
         */
        Subscription subscribe(String topic, String tagExpression, String group,
                               MessageHandler messageHandler);

        /**
         * 订阅并提供原生消息头；旧订阅器通过空头集合维持原签名行为。
         *
         * @param topic          物理 topic
         * @param tagExpression  tag 表达式
         * @param group          消费组
         * @param messageHandler 带原生头的处理器
         * @return 订阅句柄
         */
        default Subscription subscribe(String topic, String tagExpression, String group,
                                       HeaderMessageHandler messageHandler) {
            return subscribe(topic, tagExpression, group,
                    (messageId, correlationId, payload, ackCallback) ->
                            messageHandler.onMessage(messageId, correlationId,
                                    Collections.emptyMap(), payload, ackCallback));
        }
    }

    /**
     * 消息处理器签名。
     */
    @FunctionalInterface
    public interface MessageHandler {

        /**
         * 处理一条已投递消息。
         *
         * @param messageId     消息标识
         * @param correlationId 关联标识
         * @param payload       消息内容
         * @param ackCallback   确认结果回调
         */
        void onMessage(String messageId, String correlationId, byte[] payload,
                       java.util.function.Consumer<Boolean> ackCallback);
    }

    /**
     * 原生消息头处理器；供应商 SDK 适配层应传入真实消息属性。
     */
    @FunctionalInterface
    public interface HeaderMessageHandler {

        /**
         * 处理携带原生消息头的投递。
         *
         * @param messageId     消息标识
         * @param correlationId 关联标识
         * @param headers       broker 消息头
         * @param payload       消息内容
         * @param ackCallback   确认结果回调
         */
        void onMessage(String messageId, String correlationId, Map<String, String> headers,
                       byte[] payload, java.util.function.Consumer<Boolean> ackCallback);
    }

    /**
     * 订阅句柄。
     */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {

        /**
         * 结束当前组件持有的资源。
         */
        @Override
        void close();
    }

    /**
     * 默认内存发布器（本地联调/测试）：把消息路由到同进程内订阅者。
     */
    public final static class DeliveredMessage {

        /**
         * Java 序列化版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 消息标识。
         */
        private final String messageId;

        /**
         * 用于关联同一次操作产生的事件或消息的标识。
         */
        private final String correlationId;

        /**
         * 消息转移或发送时使用的序列化负载。
         */
        private final byte[] payload;

        /**
         * 在原生代理消息和 DDD4J 之间透传的头字段。
         */
        private final Map<String, String> headers;

        /**
         * 原生代理暴露的投递确认回调。
         */
        private final java.util.function.Consumer<Boolean> ackCallback;

        /**
         * 创建 TDMQ 已投递消息快照。
         *
         * @param messageId     消息标识
         * @param correlationId 关联标识
         * @param payload       消息内容
         * @param ackCallback   确认结果回调
         */
        public DeliveredMessage(String messageId, String correlationId, byte[] payload,
                                java.util.function.Consumer<Boolean> ackCallback) {
            this(messageId, correlationId, payload, Collections.emptyMap(), ackCallback);
        }

        /**
         * 创建携带原生消息头的 TDMQ 投递快照。
         *
         * @param messageId     消息标识
         * @param correlationId 关联标识
         * @param payload       消息内容
         * @param headers       broker 消息头
         * @param ackCallback   确认结果回调
         */
        @JsonCreator()
        public DeliveredMessage(@JsonProperty("messageId") String messageId,
                                @JsonProperty("correlationId") String correlationId,
                                @JsonProperty("payload") byte[] payload,
                                @JsonProperty("headers") Map<String, String> headers,
                                @JsonProperty("ackCallback") java.util.function.Consumer<Boolean> ackCallback) {
            this.messageId = messageId;
            this.correlationId = correlationId;
            this.payload = payload;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.nonNull(headers) ? headers : Collections.emptyMap()));
            this.ackCallback = ackCallback;
        }

        /**
         * @return 消息标识
         */
        @JsonProperty("messageId")
        public String messageId() {
            return messageId;
        }

        /**
         * @return 关联标识
         */
        @JsonProperty("correlationId")
        public String correlationId() {
            return correlationId;
        }

        /**
         * @return 消息内容
         */
        @JsonProperty("payload")
        public byte[] payload() {
            return payload;
        }

        /**
         * @return 不可变 broker 消息头
         */
        @JsonProperty("headers")
        public Map<String, String> headers() {
            return headers;
        }

        /**
         * @return 确认结果回调
         */
        @JsonProperty("ackCallback")
        public java.util.function.Consumer<Boolean> ackCallback() {
            return ackCallback;
        }

        /**
         * 根据<code>messageId</code>、<code>correlationId</code>、<code>payload</code>、<code>headers</code>、<code>ackCallback</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            DeliveredMessage other = (DeliveredMessage) obj;
            return Objects.equals(this.messageId, other.messageId) && Objects.equals(this.correlationId, other.correlationId) && Objects.equals(this.payload, other.payload) && Objects.equals(this.headers, other.headers) && Objects.equals(this.ackCallback, other.ackCallback);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(messageId);
            result = 31 * result + Objects.hashCode(correlationId);
            result = 31 * result + Objects.hashCode(payload);
            result = 31 * result + Objects.hashCode(headers);
            result = 31 * result + Objects.hashCode(ackCallback);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>messageId</code>、<code>correlationId</code>、<code>payload</code>、<code>headers</code>、<code>ackCallback</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "DeliveredMessage[messageId=" + messageId + ", correlationId=" + correlationId + ", payload=" + payload + ", headers=" + headers + ", ackCallback=" + ackCallback + "]";
        }
    }

    /**
     * 内存发布器实现。
     */
    static final class InMemoryBrokerPublisher implements BrokerPublisher {

        /**
         * 按消息主题记录当前订阅及其监听器。
         */
        private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers;

        /**
         * 用共享订阅表创建内存发布端。
         *
         * @param topicSubscribers 按代理主题保存的内存订阅回调映射
         */
        InMemoryBrokerPublisher(java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers) {
            this.topicSubscribers = topicSubscribers;
        }

        /**
         * 将传入的事件交给当前发布通道。
         *
         * <p>此实现调用 <code>publish(topic, tag, tenantId, key, payload, Collections.emptyMap())</code>。</p>
         *
         * @param topic    物理 topic（含 namespace 前缀）
         * @param tag      tag，可为 null
         * @param tenantId 租户 ID
         * @param key      业务 Key
         * @param payload  序列化字节流
         */
        @Override
        public void publish(String topic, String tag, String tenantId, String key, byte[] payload) {
            publish(topic, tag, tenantId, key, payload, Collections.emptyMap());
        }

        /**
         * 将传入的事件交给当前发布通道。
         *
         * <p>此实现调用 <code>topicSubscribers.get(topic)</code>。</p>
         *
         * @param topic    物理 topic
         * @param tag      tag
         * @param tenantId 租户 ID
         * @param key      业务 Key
         * @param payload  序列化内容
         * @param headers  broker 消息头
         */
        @Override
        public void publish(String topic, String tag, String tenantId, String key,
                            byte[] payload, Map<String, String> headers) {
            java.util.List<Consumer<DeliveredMessage>> entries = topicSubscribers.get(topic);
            if (Objects.isNull(entries) || entries.isEmpty()) {
                return;
            }
            String messageId = StrKit.isEmpty(key) ? java.util.UUID.randomUUID().toString() : key;
            for (Consumer<DeliveredMessage> entry : entries) {
                entry.accept(new DeliveredMessage(
                        messageId,
                        String.valueOf(System.nanoTime()),
                        payload,
                        headers,
                        ack -> {
                        }));
            }
        }
    }

    /**
     * 内存订阅器实现（仅本地联调）。
     */
    static final class InMemoryBrokerSubscriber implements BrokerSubscriber {

        /**
         * 按消息主题记录当前订阅及其监听器。
         */
        private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers;

        /**
         * 用共享订阅表创建内存订阅端。
         *
         * @param topicSubscribers 按代理主题保存的内存订阅回调映射
         */
        InMemoryBrokerSubscriber(java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers) {
            this.topicSubscribers = topicSubscribers;
        }

        /**
         * 按给定条件注册消费监听。
         *
         * @param topic         物理 topic
         * @param tagExpression tag 表达式
         * @param group         订阅组
         * @param handler       容器已匹配的请求处理器
         * @return 订阅句柄
         */
        @Override
        public Subscription subscribe(String topic, String tagExpression, String group,
                                      MessageHandler handler) {
            return subscribe(topic, tagExpression, group,
                    (messageId, correlationId, headers, payload, ackCallback) ->
                            handler.onMessage(messageId, correlationId, payload, ackCallback));
        }

        /**
         * 按给定条件注册消费监听。
         *
         * <p>此实现调用 <code>entries.add(consumer)</code>。</p>
         *
         * @param topic         物理 topic
         * @param tagExpression tag 表达式
         * @param group         消费组
         * @param handler       容器已匹配的请求处理器
         * @return 订阅句柄
         */
        @Override
        public Subscription subscribe(String topic, String tagExpression, String group,
                                      HeaderMessageHandler handler) {
            Consumer<DeliveredMessage> consumer = msg ->
                    handler.onMessage(msg.messageId(), msg.correlationId(), msg.headers(), msg.payload(), msg.ackCallback());
            java.util.List<Consumer<DeliveredMessage>> entries =
                    topicSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
            entries.add(consumer);
            return () -> {
                java.util.List<Consumer<DeliveredMessage>> list = topicSubscribers.get(topic);
                if (Objects.nonNull(list)) {
                    list.remove(consumer);
                }
            };
        }
    }
}
