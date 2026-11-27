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

    /** \u7b49\u5f85 Kafka \u539f\u751f\u53d1\u5e03\u786e\u8ba4\u7684\u9ed8\u8ba4\u8d85\u65f6\u6beb\u79d2\u6570\u3002 */
    private static final long DEFAULT_PUBLISH_ACK_TIMEOUT_MILLIS = 30_000L;
    /**
     * KafkaMQProperties 用于懒构造
     */
    private final KafkaMQProperties properties;
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("kafka");
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
    private final AtomicBoolean closed = new AtomicBoolean();
    /**
     * 已注入或懒构造的 Kafka producer
     */
    private Producer<String, String> producer;
    /** \u7528\u4e8e\u63a5\u6536 Kafka \u53d1\u5e03\u5b8c\u6210\u901a\u77e5\u7684\u56de\u8c03\u3002 */
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
     * \u53d1\u5e03\u786e\u8ba4\u8d85\u65f6\uff08ms\uff09\uff0c\u4f18\u5148\u53d6 {@code KafkaMQProperties#getPublishAckTimeoutMillis()}\uff0c
     *  \u65e0 properties \u65f6\u56de\u843d\u5230\u9ed8\u8ba4 30s\u3002
     *
     * @return \u7b49\u5f85 Kafka \u53d1\u5e03\u786e\u8ba4\u7684\u8d85\u65f6\u6beb\u79d2\u6570
     */
    private long publishAckTimeoutMillis() {
        return Objects.nonNull(properties) ? properties.getPublishAckTimeoutMillis() : DEFAULT_PUBLISH_ACK_TIMEOUT_MILLIS;
    }

    /** {@inheritDoc} */
    @Override
    public String impl() {
        return "kafka";
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
     * Kafka topic \u7528 {@code "_"} \u62fc\u63a5 namespace\uff08\u4e0e ActiveMQ \u7684 {@code "."} \u533a\u5206\uff09\u3002
     *
     * @return \u9ed8\u8ba4\u7684\u4e3b\u9898\u540d\u8fde\u63a5\u5b57\u7b26\u4e32
     */
    @Override
    public String defaultConcat() {
        return "_";
    }

    // ========================= 生产者 =========================

    /** {@inheritDoc} */
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
     * \u521b\u5efa\u751f\u4ea7\u8005\u7684\u5355\u4e00\u5165\u53e3\uff0c\u4fbf\u4e8e\u9a8c\u8bc1\u5ba2\u6237\u7aef\u81ea\u6709\u8d44\u6e90\u7684\u5173\u95ed\u7ade\u6001\u3002
     *
     * @param props Kafka \u5ba2\u6237\u7aef\u914d\u7f6e
     * @return \u4f7f\u7528\u7ed9\u5b9a\u914d\u7f6e\u521b\u5efa\u7684 Kafka \u751f\u4ea7\u8005
     */
    Producer<String, String> createProducer(Properties props) {
        return new KafkaProducer<>(props);
    }

    /**
     * \u6839\u636e {@link PartitionKeyStrategy} \u679a\u4e3e\u8ba1\u7b97 partition key\u3002
     *  <p>Kafka \u4fdd\u8bc1\u540c key \u8fdb\u540c partition \u2192 \u540c tag / \u540c tenantId \u6d88\u606f\u4e25\u683c\u987a\u5e8f\u3002
     *  <p>TAG_TENANT/TAG/TENANT \u4e09\u79cd\u590d\u7528\u7236\u7c7b {@link #partitionKey(MQEvent)} \u9ed8\u8ba4\u884c\u4e3a\uff1bNONE \u8fd4\u56de null\uff1b
     *  CUSTOM \u65f6\u5e94\u7531\u5b50\u7c7b\u8986\u5199 {@link #partitionKey(MQEvent)} \u81ea\u5b9a\u4e49\u3002
     *
     * @param event \u51b3\u5b9a\u6d88\u606f\u5206\u533a\u7684\u53d1\u5e03\u4e8b\u4ef6
     * @return \u7528\u4e8e\u9009\u62e9 Kafka \u5206\u533a\u7684\u6d88\u606f\u952e
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
     * \u501f\u9274 4\uff1a\u901a\u8fc7 AdminClient \u81ea\u52a8\u521b\u5efa topic\uff08\u5982\u4e0d\u5b58\u5728\uff09\u3002
     *
     * @param namespace \u6d88\u606f\u4e3b\u9898\u4f7f\u7528\u7684\u547d\u540d\u7a7a\u95f4
     * @param defaultTopic \u9ed8\u8ba4\u4e3b\u9898\u540d
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

    /** {@inheritDoc} */
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
     * \u521b\u5efa\u6d88\u8d39\u8005\u7684\u5355\u4e00\u5165\u53e3\uff0c\u4fbf\u4e8e\u9a8c\u8bc1\u521d\u59cb\u5316\u5931\u8d25\u65f6\u7684\u8d44\u6e90\u56de\u6536\u5951\u7ea6\u3002
     *
     * @param props Kafka \u5ba2\u6237\u7aef\u914d\u7f6e
     * @return \u4f7f\u7528\u7ed9\u5b9a\u914d\u7f6e\u521b\u5efa\u7684 Kafka \u6d88\u8d39\u8005
     */
    org.apache.kafka.clients.consumer.Consumer<String, String> createConsumer(Properties props) {
        return new KafkaConsumer<>(props);
    }

    /**
     * \u521b\u5efa\u6d88\u8d39\u8005\u4e13\u5c5e\u6267\u884c\u5668\u7684\u5355\u4e00\u5165\u53e3\uff0c\u5931\u8d25\u521d\u59cb\u5316\u65f6\u7531\u5ba2\u6237\u7aef\u8d1f\u8d23\u56de\u6536\u3002
     *
     * @param listener \u63a5\u6536\u6d88\u606f\u7684\u76d1\u542c\u5668\u5b9a\u4e49
     * @return \u8fd0\u884c\u76d1\u542c\u5faa\u73af\u7684\u6d88\u8d39\u7ebf\u7a0b\u6267\u884c\u5668
     */
    ExecutorService createConsumerExecutor(MQListener listener) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread workerThread = new Thread(r, "ddd4j-kafka-" + listener.getMethod().getName());
            workerThread.setDaemon(true);
            return workerThread;
        });
    }

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
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
     * \u5904\u7406\u5355\u6761 Kafka \u8bb0\u5f55\u3002\u6210\u529f\u6216\u660e\u786e\u8fc7\u6ee4\u540e\u63d0\u4ea4\u5f53\u524d\u504f\u79fb\uff1b\u5931\u8d25\u65f6\u56de\u9000 position\uff0c\u7981\u6b62\u63d0\u4ea4\u4e22\u5931\u6d88\u606f\u3002
     *
     * @param listener \u63a5\u6536\u6d88\u606f\u7684\u76d1\u542c\u5668\u5b9a\u4e49
     * @param mqProperties \u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684\u6d88\u606f\u961f\u5217\u914d\u7f6e
     * @param consumer \u5f53\u524d\u63a5\u6536\u8bb0\u5f55\u7684 Kafka \u6d88\u8d39\u8005
     * @param record \u5f85\u4ea4\u7ed9\u76d1\u542c\u5668\u7684 Kafka \u6d88\u8d39\u8bb0\u5f55
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
                // seek 回退 offset 后必须中断当前批次循环，否则同批次后续 record 会被重复消费。
                throw new BatchRetryException("seek to " + record.offset() + " for retry", exception);
            }
        }
    }

    // ========================= 消费者 =========================

    /**
     * \u6784\u9020 consumer group.id\uff08\u515c\u5e95\uff09\u3002
     *
     * @param listener \u63a5\u6536\u6d88\u606f\u7684\u76d1\u542c\u5668\u5b9a\u4e49
     * @return Kafka \u6d88\u8d39\u7ec4\u6807\u8bc6
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
         * \u4fdd\u5b58 Kafka \u6279\u6b21\u91cd\u8bd5\u8bca\u65ad\u53ca\u5176\u539f\u56e0\u3002
         * @param message \u8bf4\u660e\u6279\u6b21\u9700\u8981\u91cd\u8bd5\u7684\u8bca\u65ad\u6d88\u606f
         * @param cause \u89e6\u53d1\u91cd\u8bd5\u7684\u539f\u59cb\u5f02\u5e38
         */
        BatchRetryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 异步发送回调（统一收口，不阻塞 producer.send()）。
     */
    public static final class SendCallback implements Callback {

        /** \u6d88\u606f\u6295\u9012\u6216\u8ba2\u9605\u7684\u4e3b\u9898\u3002 */
        private final String topic;
        /** \u6d88\u606f\u8f6c\u79fb\u6216\u53d1\u9001\u65f6\u4f7f\u7528\u7684\u5e8f\u5217\u5316\u8d1f\u8f7d\u3002 */
        private final String payload;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>SendCallback</code> \u5b9e\u4f8b\u3002
         * @param topic \u6d88\u606f\u6295\u9012\u6216\u8ba2\u9605\u7684\u4e3b\u9898
         * @param payload <code>payload</code> \u5b57\u6bb5
         */
        SendCallback(String topic, String payload) {
            this.topic = topic;
            this.payload = payload;
        }

        /**
         * \u8bb0\u5f55 Kafka \u53d1\u5e03\u5931\u8d25\u539f\u56e0\uff0c\u6210\u529f\u65f6\u8bb0\u5f55\u4e3b\u9898\u3001\u5206\u533a\u53ca\u504f\u79fb\u3002
         * @param metadata \u4ee3\u7406\u8fd4\u56de\u7684\u53d1\u5e03\u5143\u6570\u636e
         * @param exception \u53d1\u5e03\u5931\u8d25\u65f6\u8fd4\u56de\u7684\u5f02\u5e38
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
        /** \u5f53\u524d\u5de5\u4f5c\u7ebf\u7a0b\u8f6e\u8be2\u3001\u6d88\u8d39\u6216\u63d0\u4ea4\u4f4d\u70b9\u65f6\u4f7f\u7528\u7684\u539f\u751f\u6d88\u8d39\u8005\u3002 */
        private final org.apache.kafka.clients.consumer.Consumer<String, String> consumer;
        /** \u5f53\u524d\u767b\u8bb0\u5e76\u63a5\u6536\u6d88\u606f\u7684\u4e1a\u52a1\u76d1\u542c\u5668\u3002 */
        private final MQListener listener;
        /** \u5f53\u524d\u76d1\u542c\u64cd\u4f5c\u4f7f\u7528\u7684 DDD4J \u6d88\u606f\u914d\u7f6e\u3002 */
        private final MQProperties mqProperties;
        /** \u8fd0\u884c\u5f53\u524d\u76d1\u542c\u5668\u6d88\u606f\u8f6e\u8be2\u5faa\u73af\u7684\u6267\u884c\u5668\u3002 */
        private final ExecutorService executor;
        /** \u5b9e\u9645\u6267\u884c\u5f53\u524d Kafka \u6d88\u8d39\u5faa\u73af\u7684\u7ebf\u7a0b\u3002 */
        private volatile Thread thread;

        /**
         * \u4e3a\u76d1\u542c\u5668\u7ed1\u5b9a\u6d88\u8d39\u8005\u53ca\u72ec\u7acb\u6d88\u8d39\u6267\u884c\u5668\u3002
         * @param consumer \u672c\u6b21\u64cd\u4f5c\u6d89\u53ca\u7684\u539f\u751f\u6d88\u8d39\u8005
         * @param listener \u5f53\u524d\u4ee3\u7406\u8ba2\u9605\u5bf9\u5e94\u7684 DDD4J \u6d88\u606f\u76d1\u542c\u5668
         * @param mqProperties \u5f53\u524d\u6d88\u8d39\u5faa\u73af\u4f7f\u7528\u7684\u914d\u7f6e
         */
        private ConsumerWorker(org.apache.kafka.clients.consumer.Consumer<String, String> consumer,
                               MQListener listener, MQProperties mqProperties) {
            this.consumer = consumer;
            this.listener = listener;
            this.mqProperties = mqProperties;
            this.executor = createConsumerExecutor(listener);
        }

        /** \u5524\u9192\u5e76\u505c\u6b62\u6d88\u8d39\u5faa\u73af\uff0c\u4ece\u5916\u90e8\u8c03\u7528\u65f6\u7b49\u5f85\u7ebf\u7a0b\u9000\u51fa\uff0c\u8d85\u65f6\u540e\u5f3a\u5236\u6e05\u7406\u3002 */
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

        /** {@inheritDoc} */
        @Override
        public void run() {
            thread = Thread.currentThread();
            try {
                while (!closed.get() && !thread.isInterrupted()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    try {
                        for (ConsumerRecord<String, String> record : records) {
                            if (closed.get()) {
                                break;
                            }
                            handleRecord(listener, mqProperties, consumer, record);
                        }
                    } catch (BatchRetryException exception) {
                        log.debug("Kafka batch interrupted for retry: {}", exception.getMessage());
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
