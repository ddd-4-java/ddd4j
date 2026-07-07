package io.ddd4j.mq.kafka;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Kafka 客户端实现（对齐 base-mq KafkaClient，纯 Java 零 Spring 依赖）。
 *
 * <p>命名 {@code KafkaMQClient} 避免与 {@link KafkaConsumer} 同名冲突。
 *
 * <p>双构造：
 * <ul>
 *   <li>{@link #KafkaMQClient(Producer)} —— 注入已初始化的原生 Kafka producer（runtime 自动装配用）</li>
 *   <li>{@link #KafkaMQClient(KafkaMQProperties)} —— 自行根据 properties 构造 producer</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : kafkaMQClient ###")
public class KafkaMQClient implements MQClient {

    /** 已注入或懒构造的 Kafka producer */
    private Producer<String, String> producer;
    /** KafkaMQProperties 用于懒构造 */
    private final KafkaMQProperties properties;

    /** 构造方法 1：注入原生 producer */
    public KafkaMQClient(Producer<String, String> producer) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.properties = null;
    }

    /** 构造方法 2：自行根据 properties 构造 producer（lazy） */
    public KafkaMQClient(KafkaMQProperties properties) {
        this.producer = null;
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "kafka";
    }

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        if (Objects.isNull(producer)) {
            // 懒构造：从 KafkaMQProperties + MQProperties 合并
            Properties props = new Properties();
            props.put("bootstrap.servers", properties.getBootstrapServers());
            props.put("acks", "all");
            props.put("retries", mqProperties.getRetries());
            props.put("batch.size", 16384);
            props.put("linger.ms", 1);
            props.put("buffer.memory", 33554432);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            this.producer = new KafkaProducer<>(props);
        }
        Producer<String, String> p = this.producer;
        return mqEvent -> {
            String payload = serialization().serialize(mqEvent);
            String namespace = Objects.nonNull(mqEvent.getNamespace()) ? mqEvent.getNamespace() : mqProperties.getNamespace();
            String topic = namespace + "_" + mqEvent.getTopic();
            log.info("Publish MQ [{}]: {}", topic, payload);
            try {
                p.send(new ProducerRecord<>(topic, payload));
            } catch (Exception e) {
                log.error("Publish MQ [{}]: {} failed!", topic, payload, e);
            }
        };
    }

    @Override
    public boolean initConsumer(MQListener mqListener, MQProperties mqProperties) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", properties.getBootstrapServers());
        props.put("group.id", Objects.nonNull(mqListener.getGroup()) && !mqListener.getGroup().isEmpty()
                ? mqListener.getGroup()
                : "ddd4j-" + mqListener.getMethod().getName());
        props.put("enable.auto.commit", mqProperties.isAutoAck());
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(
                (Objects.nonNull(mqListener.getNamespace()) ? mqListener.getNamespace() : "") + "_" + mqListener.getTopic()));
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ddd4j-kafka-" + mqListener.getMethod().getName());
            t.setDaemon(true);
            return t;
        }).submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    String payload = record.value();
                    MQEvent mqEvent = serialization().deserialize(payload, mqListener.payloadType());
                    if (mqEvent == null) {
                        consumer.commitSync();
                        log.warn("Consume MQ [{}] failed: the mqEvent is null", mqListener.getTopic());
                        continue;
                    }
                    if (!TagMatcher.match(mqEvent.getTag(), mqListener.getTags())) {
                        continue;
                    }
                    try {
                        consume(mqListener, mqEvent);
                        if (!mqProperties.isAutoAck()) {
                            consumer.commitSync();
                        }
                    } catch (Throwable e) {
                        log.error("Consume MQ [{}] failed: {}", mqListener.getTopic(), serialization().serialize(mqEvent), e);
                        if (!mqProperties.isAutoAck()) {
                            consumer.commitSync();
                        }
                    }
                }
            }
        });
        return true;
    }
}
