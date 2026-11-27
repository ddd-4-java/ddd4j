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
package io.ddd4j.mq.sqs;

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
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * AWS SQS 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>SQS 没有 topic/tag 概念：{@code MQListener.topic} 必须直接是 queueUrl。tag 通过
 * {@link MessageHeaders#HEADER_DESTINATION_TAG} 属性传递，仅用于客户端过滤。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : SqsMQClient ###")
public class SqsMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final SqsProperties properties;
    /** \u8d1f\u8d23\u8f6e\u8be2 SQS \u961f\u5217\u7684\u6d88\u8d39\u8005\u4efb\u52a1\u3002 */
    private final List<ScheduledExecutorService> pollers = new CopyOnWriteArrayList<>();
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("sqs");
    /** \u4fdd\u8bc1\u53d1\u9001\u64cd\u4f5c\u4e0e SQS \u5ba2\u6237\u7aef\u5173\u95ed\u4e4b\u95f4\u7684\u4e92\u65a5\u3002 */
    private final ReentrantReadWriteLock sendLifecycleLock = new ReentrantReadWriteLock(true);
    /** \u4fdd\u8bc1\u63a5\u6536\u64cd\u4f5c\u4e0e SQS \u5ba2\u6237\u7aef\u5173\u95ed\u4e4b\u95f4\u7684\u4e92\u65a5\u3002 */
    private final ReentrantReadWriteLock receiveLifecycleLock = new ReentrantReadWriteLock(true);
    /** \u7528\u6765\u53d1\u9001\u548c\u63a5\u6536 SQS \u6d88\u606f\u7684\u539f\u751f\u5ba2\u6237\u7aef\u3002 */
    private volatile SqsClient client;
    /** \u662f\u5426\u5df2\u5728\u751f\u547d\u5468\u671f\u4e2d\u767b\u8bb0\u7b49\u5f85\u8fdb\u884c\u4e2d\u6d88\u8d39\u7ed3\u675f\u7684\u52a8\u4f5c\u3002 */
    private boolean receiveDrainRegistered;
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
    private volatile boolean closed;

    /**
     * 构造 1：传入配置，首次初始化生产者或消费者时 lazy 创建 SqsClient。
     *
     * @param properties SQS 连接配置
     */
    public SqsMQClient(SqsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 构造 2：注入已初始化的原生 {@link SqsClient}（用于 runtime 集成自动注入）。
     *
     * @param client 已初始化的 SQS 客户端
     */
    public SqsMQClient(SqsClient client) {
        this.client = Objects.requireNonNull(client, "SqsClient");
        this.properties = new SqsProperties();
    }

    /**
     * \u5c06\u6587\u672c\u5305\u88c5\u6210 SQS \u5b57\u7b26\u4e32\u6d88\u606f\u5c5e\u6027\u3002
     * @param value \u5f53\u524d\u8981\u5199\u5165\u4ee3\u7406\u5c5e\u6027\u7684\u6570\u636e
     * @return \u53ef\u5199\u5165\u4ee3\u7406\u8bf7\u6c42\u7684\u6d88\u606f\u5c5e\u6027\u503c
     */
    private static MessageAttributeValue attrValue(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    /**
     * \u67e5\u627e SQS \u539f\u751f\u6d88\u606f\u7684\u6307\u5b9a\u5b57\u7b26\u4e32\u5c5e\u6027\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @param key \u5f85\u67e5\u8be2\u7684\u6d88\u606f\u5934\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u5339\u914d\u5230\u7684\u5b57\u7b26\u4e32\u5c5e\u6027\u503c\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    static String attr(Message message, String key) {
        if (Objects.isNull(message.messageAttributes())) {
            return null;
        }
        software.amazon.awssdk.services.sqs.model.MessageAttributeValue v = message.messageAttributes().get(key);
        return Objects.isNull(v) ? null : v.stringValue();
    }

    /**
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(Message message) {
        String messageId = attr(message, MessageHeaders.HEADER_MESSAGE_ID);
        if (Objects.isNull(messageId)) {
            messageId = attr(message, MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
        }
        return messageId;
    }

    // ========================= 生产者 =========================

    /** {@inheritDoc} */
    @Override
    public String impl() {
        return "sqs";
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

    /**
     * SQS \u65e0\u539f\u751f tag selector \u673a\u5236\uff0ctag \u8fc7\u6ee4\u53ea\u80fd\u5728\u5e94\u7528\u5c42\u7528 {@link TagMatcher#match} \u5b8c\u6210
     *  \uff08\u4e0d\u5339\u914d\u7684\u6d88\u606f\u76f4\u63a5 {@code deleteMessage} \u4e22\u5f03\uff0c\u907f\u514d\u65e0\u9650\u91cd\u6295\uff09\u3002\u6545\u8986\u5199\u8fd4\u56de false\u3002
     *
     * @return \u5f53\u524d\u4ee3\u7406\u662f\u5426\u652f\u6301\u6309\u6807\u7b7e\u8fc7\u6ee4
     */
    @Override
    public boolean supportsBrokerTagFilter() {
        return false;
    }

    // ========================= 消费者 =========================

    /** {@inheritDoc} */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        SqsClient publisherClient = initializeClient();
        return event -> publish(publisherClient, event);
    }

    /**
     * \u628a\u5e8f\u5217\u5316\u4e8b\u4ef6\u548c\u6d88\u606f\u5143\u6570\u636e\u53d1\u9001\u5230\u6307\u5b9a\u7684\u539f\u751f\u4ee3\u7406\u3002
     * @param publisherClient \u7528\u6765\u53d1\u9001\u5f53\u524d\u4e8b\u4ef6\u7684 SQS \u5ba2\u6237\u7aef
     * @param event \u5f53\u524d\u53d1\u5e03\u6216\u5f85\u8865\u9f50\u5143\u6570\u636e\u7684 DDD4J \u6d88\u606f\u4e8b\u4ef6
     */
    private void publish(SqsClient publisherClient, MQEvent event) {
        if (closed || client != publisherClient) {
            throw new IllegalStateException("SQS client is closed");
        }
        try {
            String queueUrl = event.getTopic();
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            if (Objects.nonNull(queueUrl)) {
                attrs.put(MessageHeaders.HEADER_DESTINATION_TOPIC, attrValue(queueUrl));
            }
            if (Objects.nonNull(event.getTenantId())) {
                attrs.put(MessageHeaders.HEADER_TENANT_ID, attrValue(event.getTenantId()));
            }
            if (Objects.nonNull(event.getMsgId())) {
                attrs.put(MessageHeaders.HEADER_MESSAGE_ID, attrValue(event.getMsgId()));
            }
            if (Objects.nonNull(event.getTag())) {
                attrs.put(tagHeaderKey(), attrValue(event.getTag()));
            }
            for (Map.Entry<String, String> header : event.headerMap().entrySet()) {
                attrs.put(header.getKey(), attrValue(header.getValue()));
            }
            String payload = serialization().serialize(event).toString();
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(payload)
                    .messageAttributes(attrs)
                    .build();
            // 序列化及日志在锁外，关闭只等待已经进入 SDK 的同步发送。
            sendLifecycleLock.readLock().lock();
            try {
                if (closed || client != publisherClient) {
                    throw new IllegalStateException("SQS client is closed");
                }
                publisherClient.sendMessage(request);
            } finally {
                sendLifecycleLock.readLock().unlock();
            }
            logger().info("Publish MQ [{}]: {}", queueUrl, payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Publish SQS event failed", ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        String queueUrl = listener.getTopic();
        if (Objects.isNull(queueUrl) || !(queueUrl.startsWith("http://") || queueUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("SQS MQListener.topic must be a queueUrl (https://...). Got: " + queueUrl);
        }
        if (properties.getPollIntervalMs() <= 0) {
            throw new IllegalArgumentException("SQS pollIntervalMs must be positive");
        }
        SqsClient consumerClient = initializeClient();
        AtomicBoolean running = new AtomicBoolean(true);
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sqs-consumer-" + listener.getRouteExpression(this.defaultConcat()));
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(() -> {
            if (!running.get()) {
                return;
            }
            try {
                List<Message> messages;
                receiveLifecycleLock.readLock().lock();
                try {
                    if (closed || !running.get()) {
                        return;
                    }
                    messages = consumerClient.receiveMessage(ReceiveMessageRequest.builder()
                                    .queueUrl(queueUrl)
                                    .maxNumberOfMessages(properties.getMaxNumberOfMessages())
                                    .waitTimeSeconds(properties.getWaitTimeSeconds())
                                    .messageAttributeNames("All")
                                    .build())
                            .messages();
                } finally {
                    receiveLifecycleLock.readLock().unlock();
                }
                if (closed || !running.get()) {
                    return;
                }
                for (Message message : messages) {
                    handleMessage(consumerClient, message, queueUrl, listener);
                }
            } catch (Exception ex) {
                logger().warn("SQS receive failed: binding={}", listener.getRouteExpression(this.defaultConcat()), ex);
            }
        }, 0, properties.getPollIntervalMs(), TimeUnit.MILLISECONDS);
        pollers.add(exec);
        lifecycle.register("sqs-poller-" + listener.getRouteExpression(this.defaultConcat()), () -> {
            running.set(false);
            exec.shutdownNow();
            pollers.remove(exec);
        });
        return true;
    }

    /**
     * \u89e3\u7801\u63a5\u6536\u7684 SQS \u6d88\u606f\u5e76\u4ea4\u7ed9\u76d1\u542c\u5668\u5904\u7406\uff0c\u540c\u65f6\u4fdd\u7559\u961f\u5217\u786e\u8ba4\u4fe1\u606f\u3002
     * @param consumerClient \u8f6e\u8be2\u5f53\u524d\u961f\u5217\u7684 SQS \u5ba2\u6237\u7aef
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @param queueUrl \u5f53\u524d\u6d88\u8d39\u7684 SQS \u961f\u5217 URL
     * @param listener \u5f53\u524d\u4ee3\u7406\u8ba2\u9605\u5bf9\u5e94\u7684 DDD4J \u6d88\u606f\u76d1\u542c\u5668
     */
    private void handleMessage(SqsClient consumerClient, Message message, String queueUrl, MQListener listener) {
        String tag = attr(message, tagHeaderKey());
        if (!TagMatcher.match(tag, listener.getTags())) {
            try {
                consumerClient.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            } catch (Exception ex) {
                logger().warn("SQS deleteMessage failed: binding={}", listener.getRouteExpression(this.defaultConcat()), ex);
            }
            return;
        }
        try {
            SqsAcknowledgment ack = new SqsAcknowledgment(consumerClient, message, queueUrl, properties.isRequeueOnNack());
            String tenantId = attr(message, MessageHeaders.HEADER_TENANT_ID);
            String payload = message.body();
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                ack.ackSingle();
                return;
            }
            if (Objects.nonNull(tenantId)) {
                event.setTenantId(tenantId);
            }
            String msgId = messageId(message);
            if (Objects.nonNull(msgId)) {
                event.setMsgId(msgId);
            }
            if (Objects.nonNull(tag)) {
                event.setTag(tag);
            }
            String correlationId = attr(message, MQDeliveryHeaders.CORRELATION_ID);
            if (Objects.nonNull(correlationId)) {
                event.setCorrelationId(correlationId);
            }
            String causationId = attr(message, MQDeliveryHeaders.CAUSATION_ID);
            if (Objects.nonNull(causationId)) {
                event.setCausationId(causationId);
            }
            try {
                consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                try {
                    consumerClient.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .visibilityTimeout(0));
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
            try {
                consumerClient.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .visibilityTimeout(0));
            } catch (Exception ignore) {
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void start() {
        // SQS 守护线程在 initConsumer 已 scheduleWithFixedDelay
    }

    /**
     * \u9996\u6b21\u4f7f\u7528\u751f\u4ea7\u8005\u6216\u6d88\u8d39\u8005\u65f6\u521b\u5efa\u81ea\u6709\u5ba2\u6237\u7aef\uff0c\u907f\u514d\u4e24\u6761\u521d\u59cb\u5316\u8def\u5f84\u91cd\u590d\u521b\u5efa\u3002
     *
     * @return \u4f9b\u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684 SQS \u5ba2\u6237\u7aef
     */
    private synchronized SqsClient initializeClient() {
        if (closed) {
            throw new IllegalStateException("SQS client is closed");
        }
        // 注册与回滚使用同一个监视器，避免新一轮初始化读到清理中的客户端。
        synchronized (lifecycle) {
            if (Objects.isNull(client)) {
                SqsClient ownedClient = Objects.requireNonNull(properties.client(), "SqsClient");
                client = ownedClient;
                lifecycle.register("sqs-client", () -> {
                    sendLifecycleLock.writeLock().lock();
                    try {
                        client = null;
                        ownedClient.close();
                    } finally {
                        sendLifecycleLock.writeLock().unlock();
                    }
                });
            }
            if (!receiveDrainRegistered) {
                receiveDrainRegistered = true;
                lifecycle.register("sqs-receive-drain", () -> {
                    receiveLifecycleLock.writeLock().lock();
                    try {
                        // 回滚移除此动作，下次初始化必须重新注册。
                        receiveDrainRegistered = false;
                    } finally {
                        receiveLifecycleLock.writeLock().unlock();
                    }
                });
            }
            return client;
        }
    }

    // ========================= 关闭 =========================

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
    @Override
    public synchronized void close() {
        sendLifecycleLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
        } finally {
            sendLifecycleLock.writeLock().unlock();
        }
        // 回滚可能持有生命周期监视器等待发送，关闭进入生命周期前释放发送锁。
        try {
            lifecycle.close();
        } finally {
            pollers.clear();
            startupStatus.stopped();
        }
    }
}
