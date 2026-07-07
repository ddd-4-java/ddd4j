package io.ddd4j.mq.kafka;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Kafka 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>整合发布与消费到单一 {@link MQClient} 实现：
 * <ul>
 *   <li>{@link #initProducer} —— 创建 {@link KafkaProducer}，返回发布函数
 *       （core 自动适配为 {@link io.ddd4j.mq.event.MQEventPublisher}）。topic 使用 {@code _} 拼接 namespace_topic</li>
 *   <li>{@link #initConsumer} —— 创建 {@link KafkaConsumer} 并启动守护线程执行 {@code poll()} 轮询。
 *       每条消息按 tag 过滤后反序列化 → 构建 {@link KafkaMessageAcknowledgment} → 调 {@link #consume} 统一消费</li>
 * </ul>
 *
 * <p>Kafka 没有原生 tag 过滤机制，由 {@link TagMatcher} 自行实现；偏移量由
 * {@link KafkaMessageAcknowledgment} 内部 {@code commitSync} 手动提交（autoAck=true 时由 broker 自动提交）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class KafkaClient implements MQClient {

    private static final String DEFAULT_CONCAT = "_";

    /**
     * 已注册的消费者轮询任务，统一在 {@link #start()} 启动守护线程。
     */
    private final List<PollTask> pollTasks = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public String impl() {
        return "kafka";
    }

    // ========================= 发布 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        Properties props = producerProperties(mqProperties);
        Producer<String, String> producer = new KafkaProducer<>(props);
        return event -> publish(producer, event, mqProperties);
    }

    private void publish(Producer<String, String> producer, MQEvent event, MQProperties mqProperties) {
        try {
            String topic = resolveTopic(event.getNamespace(), event.getTopic(), mqProperties);
            String tag = event.getTag();
            String payload = serialization().serialize(event).toString();
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, tag, payload);
            addHeader(record, MessageHeaders.HEADER_DESTINATION_TOPIC, topic);
            addHeader(record, MessageHeaders.HEADER_DESTINATION_TAG, tag);
            addHeader(record, MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
            addHeader(record, MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
            producer.send(record);
            logger().info("Publish MQ [{}]: {}", topic, payload);
        } catch (Exception ex) {
            throw new java.lang.IllegalStateException("Publish Kafka event failed", ex);
        }
    }

    // ========================= 消费 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) {
        boolean autoAck = mqProperties.isAutoAck();
        String groupId = StrKit.hasText(listener.getGroup())
                ? listener.getGroup()
                : ("ddd4j-" + (StrKit.hasText(listener.getTopic()) ? listener.getTopic() : "default"));
        org.apache.kafka.clients.consumer.Consumer<String, String> consumer = new KafkaConsumer<>(
                consumerProperties(mqProperties, groupId, autoAck));
        String topic = resolveTopic(listener);
        consumer.subscribe(Collections.singletonList(topic));
        pollTasks.add(new PollTask(consumer, listener, autoAck, mqProperties));
        return true;
    }

    @Override
    public void start() {
        for (PollTask task : pollTasks) {
            task.start();
        }
    }

    /**
     * 守护线程轮询任务：执行 {@code consumer.poll()} 循环，逐条处理。
     */
    private final class PollTask {
        private final org.apache.kafka.clients.consumer.Consumer<String, String> consumer;
        private final MQListener listener;
        private final boolean autoAck;
        private final MQProperties mqProperties;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private ExecutorService executor;

        private PollTask(org.apache.kafka.clients.consumer.Consumer<String, String> consumer, MQListener listener,
                         boolean autoAck, MQProperties mqProperties) {
            this.consumer = Objects.requireNonNull(consumer, "consumer");
            this.listener = Objects.requireNonNull(listener, "listener");
            this.autoAck = autoAck;
            this.mqProperties = mqProperties;
        }

        private void start() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ddd4j-kafka-" + listener.namespaceTopicTags());
                thread.setDaemon(true);
                return thread;
            });
            executor.submit(this::pollLoop);
        }

        private void pollLoop() {
            Duration timeout = Duration.ofMillis(1000);
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(timeout);
                    for (ConsumerRecord<String, String> record : records) {
                        consumeRecord(record);
                    }
                } catch (Exception ex) {
                    if (running.get()) {
                        logger().warn("Kafka MQ consumer poll failed: binding={}",
                                listener.namespaceTopicTags(), ex);
                    }
                }
            }
        }

        private void consumeRecord(ConsumerRecord<String, String> record) {
            String tag = headerValue(record, MessageHeaders.HEADER_DESTINATION_TAG);
            if (!TagMatcher.match(tag, listener.getTags())) {
                return;
            }
            String payload = record.value();
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            KafkaMessageAcknowledgment ack = new KafkaMessageAcknowledgment(consumer, record);
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                if (!autoAck) {
                    ack.ackSingle();
                }
                return;
            }
            try {
                consume(listener, event, ack);
                // autoAck=false 时由 ack 手动提交偏移量
                if (!autoAck && !ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed: {}", listener.namespaceTopicTags(),
                        serialization().serialize(event), ex);
                if (!autoAck) {
                    // 消费失败仍提交偏移量（避免无限重试），如需重投由业务侧 nack(requeue=true) seek
                    if (!ack.isAcknowledged()) {
                        ack.ackSingle();
                    }
                }
            }
        }
    }

    // ========================= Kafka 客户端配置 =========================

    private static Properties producerProperties(MQProperties mqProperties) {
        Properties props = new Properties();
        props.put("bootstrap.servers", mqProperties.getServer());
        props.put("acks", "all");
        props.put("retries", mqProperties.getRetries());
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return props;
    }

    private static Properties consumerProperties(MQProperties mqProperties, String groupId, boolean autoAck) {
        Properties props = new Properties();
        props.put("bootstrap.servers", mqProperties.getServer());
        props.put("group.id", groupId);
        props.put("enable.auto.commit", autoAck);
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }

    // ========================= 目的地解析与工具 =========================

    private String resolveTopic(String namespace, String topic, MQProperties mqProperties) {
        String resolvedTopic = StrKit.hasText(topic) ? topic : mqProperties.getDefaultTopic();
        String resolvedNamespace = StrKit.hasText(namespace) ? namespace : mqProperties.getNamespace();
        return StrKit.hasText(resolvedNamespace) ? resolvedNamespace + DEFAULT_CONCAT + resolvedTopic : resolvedTopic;
    }

    private String resolveTopic(MQListener listener) {
        String topic = StrKit.hasText(listener.getTopic()) ? listener.getTopic() : "ddd4j.default.topic";
        String namespace = listener.getNamespace();
        return StrKit.hasText(namespace) ? namespace + DEFAULT_CONCAT + topic : topic;
    }

    private static void addHeader(ProducerRecord<String, String> record, String key, String value) {
        if (StrKit.hasText(value)) {
            record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (Objects.isNull(header) || Objects.isNull(header.value())) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
