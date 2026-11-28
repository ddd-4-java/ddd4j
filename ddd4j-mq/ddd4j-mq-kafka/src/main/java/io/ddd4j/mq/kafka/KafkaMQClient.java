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
package io.ddd4j.mq.kafka;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Kafka 客户端实现（对齐 base-mq KafkaClient，纯 Java 零 Spring 依赖）。
 *
 * <p>双构造：
 * <ul>
 *   <li>{@link #KafkaMQClient(Producer, Callback)} —— 注入已初始化的原生 Kafka producer（runtime 自动装配用）</li>
 *   <li>{@link #KafkaMQClient(KafkaMQProperties, Callback)} —— 自行根据 properties 构造 producer</li>
 * </ul>
 *
 * <p>借鉴 1：分区 key（producer 按 tag/tenantId 路由，同 key 进同 partition 保证顺序）
 * <p>借鉴 2：producer send 异步 callback（非阻塞发送、统一 ack/nack 收口）
 * <p>借鉴 4：AdminClient 自动创建 topic（启动时确保 topic 存在，分区/副本数可配）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : KafkaMQClient ###")
public class KafkaMQClient implements MQClient {

    /**
     * 等待 Kafka 原生发布确认的默认超时毫秒数。
     */
    private static final long DEFAULT_PUBLISH_ACK_TIMEOUT_MILLIS = 30_000L;
    /**
     * KafkaMQProperties 用于懒构造
     */
    private final KafkaMQProperties properties;
    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
    private final MQStartupStatus startupStatus = new MQStartupStatus("kafka");
    /**
     * 当前实例是否已关闭。
     */
    private final AtomicBoolean closed = new AtomicBoolean();
    /**
     * 已注入或懒构造的 Kafka producer
     */
    private Producer<String, String> producer;
    /**
     * 用于接收 Kafka 发布完成通知的回调。
     */
    private Callback callback;

    /**
     * 构造方法 1：注入原生 producer（runtime 自动装配用）。
     *
     * @param producer 原生 Kafka producer
     * @param callback 发布回调，可为 null
     */
    public KafkaMQClient(Producer<String, String> producer, Callback callback) {
        this.properties = null;
        this.producer = Objects.requireNonNull(producer, "KafkaMQ Producer is required");
        this.callback = callback;
    }

    /**
     * 构造方法 2：自行根据 properties 构造 producer（lazy）。
     *
     * @param properties Kafka 客户端配置
     * @param callback   发布回调，可为 null
     */
    public KafkaMQClient(KafkaMQProperties properties, Callback callback) {
        this.properties = Objects.requireNonNull(properties, "KafkaMQ Properties is required");
        this.producer = null;
        this.callback = callback;
    }

    /**
     * 发布确认超时（ms），优先取 {@code KafkaMQProperties#getPublishAckTimeoutMillis()}，
     * 无 properties 时回落到默认 30s。
     *
     * @return 等待 Kafka 发布确认的超时毫秒数
     */
    private long publishAckTimeoutMillis() {
        return Objects.nonNull(properties) ? properties.getPublishAckTimeoutMillis() : DEFAULT_PUBLISH_ACK_TIMEOUT_MILLIS;
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"kafka"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "kafka";
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
     * Kafka topic 用 {@code "_"} 拼接 namespace（与 ActiveMQ 的 {@code "."} 区分）。
     *
     * @return 默认的主题名连接字符串
     */
    @Override
    public String defaultConcat() {
        return "_";
    }

    // ========================= 生产者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>Kafka 实现调用 <code>ensureOpen()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public synchronized Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        ensureOpen();
        if (Objects.isNull(producer) && Objects.nonNull(this.properties)) {
            this.producer = createProducer(properties.producerProperties());
            Producer<String, String> ownedProducer = this.producer;
            lifecycle.register("kafka-producer-close", ownedProducer::close);
            lifecycle.register("kafka-producer-flush", ownedProducer::flush);
            // 启动时通过 AdminClient 确保 topic 存在
            if (properties.isAutoCreateTopics()) {
                ensureTopic(mqProperties.getNamespace(), mqProperties.getDefaultTopic());
            }
        }
        Producer<String, String> producer1 = this.producer;
        return mqEvent -> {
            ensureOpen();
            String payload = serialization().serialize(mqEvent);
            String topic = resolveTopic(mqEvent, mqProperties);
            String key = partitionKey(mqEvent);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
            for (Map.Entry<String, String> headerEntry : mqEvent.headerMap().entrySet()) {
                record.headers().add(headerEntry.getKey(),
                        headerEntry.getValue().getBytes(StandardCharsets.UTF_8));
            }
            try {
                // Outbox 只有在 broker 确认后才能标记成功；异步回调不能作为同步发布契约。
                producer1.send(record, Objects.nonNull(callback) ? callback : new SendCallback(topic, payload))
                        .get(publishAckTimeoutMillis(), TimeUnit.MILLISECONDS);
                log.info("Publish MQ [{}]: {}", topic, payload);
            } catch (java.util.concurrent.TimeoutException timeout) {
                // 超时意味着 broker 在配置窗口内未确认。Outbox 会标记这条为失败并重试，
                // 这里打 WARN 而不是 ERROR，避免与真正的 broker 故障混淆——重试通常能恢复。
                log.warn("Publish MQ [{}] ack timeout after {}ms: {}", topic, publishAckTimeoutMillis(), mqEvent.getMsgId());
                throw new IllegalStateException("Publish Kafka event ack timeout after "
                        + publishAckTimeoutMillis() + "ms: " + mqEvent.getMsgId(), timeout);
            } catch (Exception exception) {
                throw new IllegalStateException("Publish Kafka event failed: " + mqEvent.getMsgId(), exception);
            }
        };
    }

    /**
     * 创建生产者的单一入口，便于验证客户端自有资源的关闭竞态。
     *
     * @param props Kafka 客户端配置
     * @return 使用给定配置创建的 Kafka 生产者
     */
    Producer<String, String> createProducer(Properties props) {
        return new KafkaProducer<>(props);
    }

    /**
     * 根据 {@link PartitionKeyStrategy} 枚举计算 partition key。
     * <p>Kafka 保证同 key 进同 partition → 同 tag / 同 tenantId 消息严格顺序。
     * <p>TAG_TENANT/TAG/TENANT 三种复用父类 {@link #partitionKey(MQEvent)} 默认行为；NONE 返回 null；
     * CUSTOM 时应由子类覆写 {@link #partitionKey(MQEvent)} 自定义。
     *
     * @param event 决定消息分区的发布事件
     * @return 用于选择 Kafka 分区的消息键
     */
    @Override
    public String partitionKey(MQEvent event) {
        if (Objects.isNull(properties)) {
            return MQClient.super.partitionKey(event);  // 双构造 1：注入 producer 时走父类默认
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
     * 借鉴 4：通过 AdminClient 自动创建 topic（如不存在）。
     *
     * @param namespace    消息主题使用的命名空间
     * @param defaultTopic 默认主题名
     */
    private void ensureTopic(String namespace, String defaultTopic) {
        if (Objects.isNull(this.properties)) {
            return;
        }
        // 统一复用父类 resolveTopic（与 initProducer 内的 topic 解析保持一致）
        String topic = resolveTopic(namespace, defaultTopic, null, defaultConcat());
        try (AdminClient admin = AdminClient.create(properties.adminProperties())) {
            if (admin.listTopics().names().get().contains(topic)) {
                return;
            }
            NewTopic newTopic = new NewTopic(topic,
                    properties.getDefaultTopicPartitions(),
                    properties.getDefaultTopicReplication());
            admin.createTopics(Collections.singletonList(newTopic)).all().get();
            log.info("Kafka topic auto-created via AdminClient: {}", topic);
        } catch (Exception ex) {
            // 自动建 topic 失败不阻断 producer —— broker 可能禁用了自动创建或权限不足
            log.warn("Kafka AdminClient create topic failed (will retry on next publish): {}", ex.getMessage());
        }
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>Kafka 实现调用 <code>ensureOpen()</code>。</p>
     *
     * @param mqListener   传给 <code>properties.consumerProperties(buildGroupId(mqListener))</code>
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 消费者完成注册时为真
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public synchronized boolean initConsumer(MQListener mqListener, MQProperties mqProperties) throws Exception {
        ensureOpen();
        if (Objects.isNull(properties)) {
            return false;
        }
        Properties props = properties.consumerProperties(buildGroupId(mqListener));
        props.put("bootstrap.servers", properties.getBootstrapServers());
        org.apache.kafka.clients.consumer.Consumer<String, String> consumer = createConsumer(props);
        ConsumerWorker worker;
        try {
            worker = new ConsumerWorker(consumer, mqListener, mqProperties);
        } catch (RuntimeException exception) {
            consumer.close();
            throw exception;
        }
        try {
            consumer.subscribe(Collections.singletonList(resolveTopic(mqListener, mqProperties)));
            worker.executor.execute(worker);
            lifecycle.register("kafka-consumer-" + mqListener.getMethod().getName(), worker::close);
        } catch (RuntimeException exception) {
            worker.executor.shutdown();
            consumer.close();
            throw exception;
        }
        return true;
    }

    /**
     * 创建消费者的单一入口，便于验证初始化失败时的资源回收契约。
     *
     * @param props Kafka 客户端配置
     * @return 使用给定配置创建的 Kafka 消费者
     */
    org.apache.kafka.clients.consumer.Consumer<String, String> createConsumer(Properties props) {
        return new KafkaConsumer<>(props);
    }

    /**
     * 创建消费者专属执行器的单一入口，失败初始化时由客户端负责回收。
     *
     * @param listener 接收消息的监听器定义
     * @return 运行监听循环的消费线程执行器
     */
    ExecutorService createConsumerExecutor(MQListener listener) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread workerThread = new Thread(r, "ddd4j-kafka-" + listener.getMethod().getName());
            workerThread.setDaemon(true);
            return workerThread;
        });
    }

    /**
     * 释放消息客户端拥有的运行资源，并在退出时登记组件已停止。
     */
    @Override
    public void close() {
        synchronized (this) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
        }
        try {
            lifecycle.close();
        } finally {
            startupStatus.stopped();
        }
    }

    /**
     * 处理单条 Kafka 记录。成功或明确过滤后提交当前偏移；失败时回退 position，禁止提交丢失消息。
     *
     * @param listener     接收消息的监听器定义
     * @param mqProperties 当前适配器使用的消息队列配置
     * @param consumer     当前接收记录的 Kafka 消费者
     * @param record       待交给监听器的 Kafka 消费记录
     */
    void handleRecord(MQListener listener, MQProperties mqProperties,
                      org.apache.kafka.clients.consumer.Consumer<String, String> consumer,
                      ConsumerRecord<String, String> record) {
        String payload = record.value();
        KafkaMessageAcknowledgment acknowledgment = new KafkaMessageAcknowledgment(consumer, record);
        try {
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                acknowledgment.ackSingle();
                log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(defaultConcat()));
                return;
            }
            if (!TagMatcher.match(event.getTag(), listener.getTags())) {
                acknowledgment.ackSingle();
                return;
            }
            consume(listener, event, acknowledgment);
            if (!acknowledgment.isAcknowledged()) {
                acknowledgment.ackSingle();
            }
        } catch (WakeupException exception) {
            throw exception;
        } catch (Throwable exception) {
            log.error("Consume MQ [{}] failed: {}", listener.getTopic(), payload, exception);
            if (!acknowledgment.isAcknowledged()) {
                consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
                // 回退后中断当前分区的批次，避免越过失败记录提交后续 offset。
                throw new BatchRetryException("seek to " + record.offset() + " for retry", exception);
            }
        }
    }

    // ========================= 消费者 =========================

    /**
     * 构造 consumer group.id（兜底）。
     *
     * @param listener 接收消息的监听器定义
     * @return Kafka 消费组标识
     */
    private String buildGroupId(MQListener listener) {
        if (StrKit.isNotBlank(listener.getGroup())) {
            return listener.getGroup();
        }
        String prefix = Objects.nonNull(properties) ? properties.getGroupIdPrefix() : null;
        return (StrKit.isNotBlank(prefix) ? prefix : "ddd4j") + "-" + listener.getMethod().getName();
    }

    /**
     * 防止关闭后重新创建资源或复用发布入口。
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("KafkaMQClient is closed");
        }
    }

    /**
     * 标记当前 poll 批次需要重试（handleRecord seek 回退后抛出）。
     * 仅在消费者循环内部使用，不逃逸到外部调用方。
     */
    static final class BatchRetryException extends RuntimeException {
        /**
         * 保存 Kafka 批次重试诊断及其原因。
         *
         * @param message 说明批次需要重试的诊断消息
         * @param cause   触发重试的原始异常
         */
        BatchRetryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 异步发送回调（统一收口，不阻塞 producer.send()）。
     */
    public static final class SendCallback implements Callback {

        /**
         * 消息投递或订阅的主题。
         */
        private final String topic;
        /**
         * 消息转移或发送时使用的序列化负载。
         */
        private final String payload;

        /**
         * 用给定值初始化 <code>SendCallback</code> 实例。
         *
         * @param topic   消息投递或订阅的主题
         * @param payload <code>payload</code> 字段
         */
        SendCallback(String topic, String payload) {
            this.topic = topic;
            this.payload = payload;
        }

        /**
         * 记录 Kafka 发布失败原因，成功时记录主题、分区及偏移。
         *
         * @param metadata  代理返回的发布元数据
         * @param exception 发布失败时返回的异常
         */
        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            if (Objects.nonNull(exception)) {
                log.error("Kafka send failed: topic={}, payload={}", topic, payload, exception);
            } else if (log.isDebugEnabled()) {
                log.debug("Kafka send success: topic={}, partition={}, offset={}", metadata.topic(), metadata.partition(), metadata.offset());
            }
        }
    }

    /**
     * 单个消费者及其执行器的生命周期，异常退出时立即释放原生资源。
     */
    private final class ConsumerWorker implements Runnable {
        /**
         * 当前工作线程轮询、消费或提交位点时使用的原生消费者。
         */
        private final org.apache.kafka.clients.consumer.Consumer<String, String> consumer;
        /**
         * 当前登记并接收消息的业务监听器。
         */
        private final MQListener listener;
        /**
         * 当前监听操作使用的 DDD4J 消息配置。
         */
        private final MQProperties mqProperties;
        /**
         * 运行当前监听器消息轮询循环的执行器。
         */
        private final ExecutorService executor;
        /**
         * 实际执行当前 Kafka 消费循环的线程。
         */
        private volatile Thread thread;

        /**
         * 为监听器绑定消费者及独立消费执行器。
         *
         * @param consumer     本次操作涉及的原生消费者
         * @param listener     当前代理订阅对应的 DDD4J 消息监听器
         * @param mqProperties 当前消费循环使用的配置
         */
        private ConsumerWorker(org.apache.kafka.clients.consumer.Consumer<String, String> consumer,
                               MQListener listener, MQProperties mqProperties) {
            this.consumer = consumer;
            this.listener = listener;
            this.mqProperties = mqProperties;
            this.executor = createConsumerExecutor(listener);
        }

        /**
         * 唤醒并停止消费循环，从外部调用时等待线程退出，超时后强制清理。
         */
        private void close() {
            executor.shutdown();
            if (!executor.isTerminated()) {
                consumer.wakeup();
            }
            if (Thread.currentThread() == thread) {
                return;
            }
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("Kafka consumer did not finish shutdown within 10 seconds");
                }
            } catch (InterruptedException exception) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        /**
         * 执行当前组件的任务体。
         *
         * <p>此实现调用 <code>Thread.currentThread()</code>。</p>
         */
        @Override
        public void run() {
            thread = Thread.currentThread();
            try {
                while (!closed.get() && !thread.isInterrupted()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (TopicPartition partition : records.partitions()) {
                        try {
                            for (ConsumerRecord<String, String> record : records.records(partition)) {
                                if (closed.get()) {
                                    break;
                                }
                                handleRecord(listener, mqProperties, consumer, record);
                            }
                        } catch (BatchRetryException exception) {
                            // 其他分区的 poll position 已推进，必须继续处理其已拉取的记录。
                            log.debug("Kafka partition {} batch interrupted for retry: {}",
                                    partition, exception.getMessage());
                        }
                    }
                }
            } catch (WakeupException exception) {
                if (!closed.get()) {
                    log.error("Kafka consumer unexpectedly woken up", exception);
                }
            } catch (RuntimeException exception) {
                log.error("Kafka consumer stopped unexpectedly", exception);
            } finally {
                try {
                    consumer.close();
                } finally {
                    executor.shutdown();
                }
            }
        }
    }
}
