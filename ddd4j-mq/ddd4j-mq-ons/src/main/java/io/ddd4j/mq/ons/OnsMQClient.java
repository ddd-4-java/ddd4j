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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final OnsProperties properties;
    /** \u5f53\u524d\u9002\u914d\u5668\u5df2\u5efa\u7acb\u7684\u539f\u751f\u6d88\u8d39\u8005\u3002 */
    private final List<com.aliyun.openservices.ons.api.Consumer> consumers = new CopyOnWriteArrayList<>();
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("ons");
    /** \u7531\u8c03\u7528\u65b9\u6ce8\u5165\u4e14\u751f\u547d\u5468\u671f\u7531\u5916\u90e8\u63a7\u5236\u7684\u751f\u4ea7\u8005\u3002 */
    private final Producer injectedProducer;
    /** \u534f\u8c03\u4ee3\u7406\u751f\u4ea7\u8005\u53ca\u5176\u5728\u9014\u53d1\u9001\u7684\u4f1a\u8bdd\u3002 */
    private volatile ProducerSession producerSession;
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
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

    /** {@inheritDoc} */
    @Override
    public String impl() {
        return "ons";
    }

    /** {@inheritDoc} */
    @Override
    public MQClientLifecycle lifecycle() {
        return lifecycle;
    }

    // ========================= 生产者 =========================

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
     * \u521d\u59cb\u5316\u5e76\u542f\u52a8\u73b0\u6709\u6216\u672c\u5b9e\u4f8b\u521b\u5efa\u7684 ONS \u53d1\u5e03\u5ba2\u6237\u7aef\u3002
     * @return \u5df2\u7ecf\u542f\u52a8\u7684\u539f\u751f\u751f\u4ea7\u4f1a\u8bdd
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
     * \u7528\u6307\u5b9a\u4f1a\u8bdd\u5c5e\u6027\u521b\u5efa ONS \u6d88\u8d39\u5ba2\u6237\u7aef\u3002
     * @param sessionProperties \u5f53\u524d ONS \u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u5c5e\u6027
     * @return \u5c1a\u5f85\u8ba2\u9605\u7684\u539f\u751f\u6d88\u8d39\u8005
     */
    com.aliyun.openservices.ons.api.Consumer createConsumer(Properties sessionProperties) {
        return ONSFactory.createConsumer(sessionProperties);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
            producerSession = null;
            startupStatus.stopped();
        }
    }

    /** \u8ddf\u8e2a ONS \u751f\u4ea7\u8005\u5728\u9014\u53d1\u9001\uff0c\u5e76\u5728\u5173\u95ed\u65f6\u534f\u8c03\u53d1\u9001\u5b8c\u6210\u3002 */
    private final class ProducerSession {
        /** \u5f53\u524d\u4f1a\u8bdd\u7ba1\u7406\u7684 ONS \u751f\u4ea7\u8005\u3002 */
        private final Producer producer;
        /** \u4fdd\u62a4\u751f\u4ea7\u8005\u72b6\u6001\u548c\u5728\u9014\u53d1\u9001\u8ba1\u6570\u7684\u9501\u3002 */
        private final ReentrantLock sendLock = new ReentrantLock();
        /** \u7b49\u5f85\u751f\u4ea7\u8005\u5728\u9014\u53d1\u9001\u5b8c\u6210\u7684\u6761\u4ef6\u53d8\u91cf\u3002 */
        private final Condition sendsFinished = sendLock.newCondition();
        /** \u751f\u4ea7\u8005\u4f1a\u8bdd\u662f\u5426\u4ecd\u63a5\u53d7\u65b0\u7684\u53d1\u9001\u64cd\u4f5c\u3002 */
        private volatile boolean active = true;
        /** \u5f53\u524d\u5c1a\u672a\u7ed3\u675f\u7684\u6d88\u606f\u53d1\u9001\u6570\u91cf\u3002 */
        private int inFlight;

        /**
         * \u4ee5\u6307\u5b9a\u751f\u4ea7\u8005\u521b\u5efa\u53ef\u8ddf\u8e2a\u5728\u9014\u53d1\u9001\u7684\u4f1a\u8bdd\u3002
         * @param producer \u672c\u6b21\u64cd\u4f5c\u6d89\u53ca\u7684\u539f\u751f\u751f\u4ea7\u8005
         */
        private ProducerSession(Producer producer) {
            this.producer = producer;
        }

        /**
         * \u8bfb\u53d6<code>active</code> \u5b57\u6bb5\u3002
         * @return <code>active</code> \u5b57\u6bb5
         */
        private boolean isActive() {
            return active;
        }

        /** \u68c0\u67e5\u751f\u4ea7\u8005\u4f1a\u8bdd\u662f\u5426\u8fd8\u63a5\u53d7\u53d1\u9001\u52a8\u4f5c\u3002 */
        private void checkAvailable() {
            if (closed) {
                throw new IllegalStateException("ONS client is closed");
            }
            if (!active || producerSession != this) {
                throw new IllegalStateException("ONS producer is no longer active");
            }
        }

        /** \u5728\u751f\u4ea7\u8005\u4f1a\u8bdd\u4ecd\u53ef\u7528\u65f6\u767b\u8bb0\u4e00\u6b21\u5728\u9014\u53d1\u9001\u3002 */
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

        /** \u7ed3\u675f\u5df2\u767b\u8bb0\u7684\u53d1\u9001\uff0c\u5e76\u5728\u6ca1\u6709\u5728\u9014\u53d1\u9001\u65f6\u901a\u77e5\u7b49\u5f85\u5173\u95ed\u7684\u7ebf\u7a0b\u3002 */
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

        /** \u505c\u6b62\u5f53\u524d\u6d88\u606f\u4f1a\u8bdd\u6216\u5206\u53d1\u5668\uff0c\u5e76\u6e05\u7406\u5176\u62e5\u6709\u7684\u53d1\u9001\u8d44\u6e90\u3002 */
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
