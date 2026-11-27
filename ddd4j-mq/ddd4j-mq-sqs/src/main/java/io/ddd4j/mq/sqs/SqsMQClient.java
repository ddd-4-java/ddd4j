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

    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
    private final SqsProperties properties;
    /**
     * 负责轮询 SQS 队列的消费者任务。
     */
    private final List<ScheduledExecutorService> pollers = new CopyOnWriteArrayList<>();
    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
    private final MQStartupStatus startupStatus = new MQStartupStatus("sqs");
    /**
     * 保证发送操作与 SQS 客户端关闭之间的互斥。
     */
    private final ReentrantReadWriteLock sendLifecycleLock = new ReentrantReadWriteLock(true);
    /**
     * 保证接收操作与 SQS 客户端关闭之间的互斥。
     */
    private final ReentrantReadWriteLock receiveLifecycleLock = new ReentrantReadWriteLock(true);
    /**
     * 用来发送和接收 SQS 消息的原生客户端。
     */
    private volatile SqsClient client;
    /**
     * 是否已在生命周期中登记等待进行中消费结束的动作。
     */
    private boolean receiveDrainRegistered;
    /**
     * 当前实例是否已关闭。
     */
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
     * 将文本包装成 SQS 字符串消息属性。
     *
     * @param value 当前要写入代理属性的数据
     * @return 可写入代理请求的消息属性值
     */
    private static MessageAttributeValue attrValue(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    /**
     * 查找 SQS 原生消息的指定字符串属性。
     *
     * @param message 当前接收的原生代理消息
     * @param key     待查询的消息头或属性名称
     * @return 匹配到的字符串属性值，缺少时为空
     */
    static String attr(Message message, String key) {
        if (Objects.isNull(message.messageAttributes())) {
            return null;
        }
        software.amazon.awssdk.services.sqs.model.MessageAttributeValue v = message.messageAttributes().get(key);
        return Objects.isNull(v) ? null : v.stringValue();
    }

    /**
     * 优先从原生消息的标准头读取消息标识，缺少时回退到兼容头。
     *
     * @param message 当前接收的原生代理消息
     * @return 用于后续消费确认的业务消息标识，头均缺失时为空
     */
    static String messageId(Message message) {
        String messageId = attr(message, MessageHeaders.HEADER_MESSAGE_ID);
        if (Objects.isNull(messageId)) {
            messageId = attr(message, MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
        }
        return messageId;
    }

    // ========================= 生产者 =========================

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"sqs"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "sqs";
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
     * SQS 无原生 tag selector 机制，tag 过滤只能在应用层用 {@link TagMatcher#match} 完成
     * （不匹配的消息直接 {@code deleteMessage} 丢弃，避免无限重投）。故覆写返回 false。
     *
     * @return 当前代理是否支持按标签过滤
     */
    @Override
    public boolean supportsBrokerTagFilter() {
        return false;
    }

    // ========================= 消费者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>此实现调用 <code>initializeClient()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        SqsClient publisherClient = initializeClient();
        return event -> publish(publisherClient, event);
    }

    /**
     * 把序列化事件和消息元数据发送到指定的原生代理。
     *
     * @param publisherClient 用来发送当前事件的 SQS 客户端
     * @param event           当前发布或待补齐元数据的 DDD4J 消息事件
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

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>listener.getTopic()</code>。</p>
     *
     * @param listener     监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
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
     * 解码接收的 SQS 消息并交给监听器处理，同时保留队列确认信息。
     *
     * @param consumerClient 轮询当前队列的 SQS 客户端
     * @param message        当前接收的原生代理消息
     * @param queueUrl       当前消费的 SQS 队列 URL
     * @param listener       当前代理订阅对应的 DDD4J 消息监听器
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

    /**
     * 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。
     */
    @Override
    public void start() {
        // SQS 守护线程在 initConsumer 已 scheduleWithFixedDelay
    }

    /**
     * 首次使用生产者或消费者时创建自有客户端，避免两条初始化路径重复创建。
     *
     * @return 供当前适配器使用的 SQS 客户端
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

    /**
     * 释放消息客户端拥有的运行资源，并在退出时登记组件已停止。
     */
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
