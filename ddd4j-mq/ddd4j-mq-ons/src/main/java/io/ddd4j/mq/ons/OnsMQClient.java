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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
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

    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
    private final OnsProperties properties;
    /**
     * 当前适配器已建立的原生消费者。
     */
    private final List<com.aliyun.openservices.ons.api.Consumer> consumers = new CopyOnWriteArrayList<>();
    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
    private final MQStartupStatus startupStatus = new MQStartupStatus("ons");
    /**
     * 由调用方注入且生命周期由外部控制的生产者。
     */
    private final Producer injectedProducer;
    /**
     * 协调代理生产者及其在途发送的会话。
     */
    private volatile ProducerSession producerSession;
    /**
     * 当前实例是否已关闭。
     */
    private volatile boolean closed;

    /**
     * 构造 1：传入配置，{@link #initProducer}/{@link #initConsumer} 中通过 ONSFactory 创建原生客户端。
     *
     * @param properties ONS 配置
     */
    public OnsMQClient(OnsProperties properties) {
        this(null, properties);
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
        this.injectedProducer = producer;
    }

    /**
     * 优先从原生消息的标准头读取消息标识，缺少时回退到兼容头。
     *
     * @param message 当前接收的原生代理消息
     * @return 用于后续消费确认的业务消息标识，头均缺失时为空
     */
    static String messageId(Message message) {
        String messageId = message.getUserProperties(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : message.getUserProperties(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

    /**
     * 将 DDD4J 消息元数据写入 ONS 原生消息用户属性。
     *
     * @param message 当前接收的原生代理消息
     * @param event   当前发布或待补齐元数据的 DDD4J 消息事件
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
     * 用指定会话属性创建 ONS 发布客户端。
     *
     * @param sessionProperties 当前 ONS 客户端的会话属性
     * @return 尚待启动的原生生产者
     */
    Producer createProducer(Properties sessionProperties) {
        return ONSFactory.createProducer(sessionProperties);
    }

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 此实现调用 <code>producerSession.isActive()</code>。</p>
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
            if (Objects.isNull(producerSession) || !producerSession.isActive()) {
                producerSession = startProducer();
            }
            ProducerSession session = producerSession;
            return event -> {
                session.checkAvailable();
                String topic;
                Message msg;
                String serialized;
                try {
                    topic = resolveTopic(event, mqProperties);
                    String tag = event.getTag();
                    serialized = serialization().serialize(event).toString();
                    msg = new Message(topic, tag, event.getMsgId(), serialized.getBytes(StandardCharsets.UTF_8));
                    msg.setKey(event.getMsgId());
                    if (Objects.nonNull(event.getTenantId())) {
                        msg.putUserProperties(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
                    }
                    for (Map.Entry<String, String> header : event.headerMap().entrySet()) {
                        msg.putUserProperties(header.getKey(), header.getValue());
                    }
                } catch (Exception ex) {
                    throw new IllegalStateException("Publish ONS event failed", ex);
                }

                session.beginSend();
                try {
                    try {
                        session.producer.send(msg);
                    } finally {
                        session.endSend();
                    }
                    logger().info("Publish MQ [{}]: {}", topic, serialized);
                } catch (Exception ex) {
                    throw new IllegalStateException("Publish ONS event failed", ex);
                }
            };
        } catch (Exception ex) {
            throw new IllegalStateException("Init ONS producer failed", ex);
        }
    }

    /**
     * 初始化并启动现有或本实例创建的 ONS 发布客户端。
     *
     * @return 已经启动的原生生产会话
     */
    private ProducerSession startProducer() {
        boolean owned = Objects.isNull(injectedProducer);
        Producer producer = owned
                ? createProducer(properties.sessionProperties(properties.getProducerId()))
                : injectedProducer;
        ProducerSession session = new ProducerSession(producer);
        try {
            producer.start();
            if (owned) {
                lifecycle.register("ons-producer", session::shutdown);
                if (!session.isActive()) {
                    throw new IllegalStateException("ONS producer is no longer active");
                }
            }
        } catch (RuntimeException | Error exception) {
            if (owned) {
                try {
                    session.shutdown();
                } catch (RuntimeException | Error shutdownFailure) {
                    if (exception != shutdownFailure) {
                        exception.addSuppressed(shutdownFailure);
                    }
                }
            }
            throw exception;
        }
        return session;
    }

    /**
     * 用指定会话属性创建 ONS 消费客户端。
     *
     * @param sessionProperties 当前 ONS 客户端的会话属性
     * @return 尚待订阅的原生消费者
     */
    com.aliyun.openservices.ons.api.Consumer createConsumer(Properties sessionProperties) {
        return ONSFactory.createConsumer(sessionProperties);
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 此实现调用 <code>StrKit.hasText(listener.getGroup())</code>。</p>
     *
     * @param listener     监听器定义
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

    /**
     * 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。
     */
    @Override
    public void start() {
        // ONS producer/consumer 在各自创建时已启动；此方法留作 future 扩展。
    }

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
            lifecycle.close();
        } finally {
            consumers.clear();
            producerSession = null;
            startupStatus.stopped();
        }
    }

    /**
     * 跟踪 ONS 生产者在途发送，并在关闭时协调发送完成。
     */
    private final class ProducerSession {
        /**
         * 当前会话管理的 ONS 生产者。
         */
        private final Producer producer;
        /**
         * 保护生产者状态和在途发送计数的锁。
         */
        private final ReentrantLock sendLock = new ReentrantLock();
        /**
         * 等待生产者在途发送完成的条件变量。
         */
        private final Condition sendsFinished = sendLock.newCondition();
        /**
         * 生产者会话是否仍接受新的发送操作。
         */
        private volatile boolean active = true;
        /**
         * 当前尚未结束的消息发送数量。
         */
        private int inFlight;

        /**
         * 以指定生产者创建可跟踪在途发送的会话。
         *
         * @param producer 本次操作涉及的原生生产者
         */
        private ProducerSession(Producer producer) {
            this.producer = producer;
        }

        /**
         * 读取<code>active</code> 字段。
         *
         * @return <code>active</code> 字段
         */
        private boolean isActive() {
            return active;
        }

        /**
         * 检查生产者会话是否还接受发送动作。
         */
        private void checkAvailable() {
            if (closed) {
                throw new IllegalStateException("ONS client is closed");
            }
            if (!active || producerSession != this) {
                throw new IllegalStateException("ONS producer is no longer active");
            }
        }

        /**
         * 在生产者会话仍可用时登记一次在途发送。
         */
        private void beginSend() {
            sendLock.lock();
            try {
                // 序列化完成后重新确认会话仍有效，再受理本次 SDK 发送。
                checkAvailable();
                inFlight++;
            } finally {
                sendLock.unlock();
            }
        }

        /**
         * 结束已登记的发送，并在没有在途发送时通知等待关闭的线程。
         */
        private void endSend() {
            sendLock.lock();
            try {
                inFlight--;
                if (!active && inFlight == 0) {
                    sendsFinished.signalAll();
                }
            } finally {
                sendLock.unlock();
            }
        }

        /**
         * 停止当前消息会话或分发器，并清理其拥有的发送资源。
         */
        private void shutdown() {
            sendLock.lock();
            try {
                if (!active) {
                    return;
                }
                // 回滚持有生命周期锁，此处只等待已受理的发送，不获取适配器锁。
                active = false;
                while (inFlight > 0) {
                    sendsFinished.awaitUninterruptibly();
                }
            } finally {
                sendLock.unlock();
            }
            producer.shutdown();
        }
    }
}
