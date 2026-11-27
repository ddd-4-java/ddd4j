package io.ddd4j.mq.kafka;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Kafka 客户端实现（对齐 base-mq KafkaClient，纯 Java 零 Spring 依赖）。
 *
 * <p>双构造：
 * <ul>
 *   <li>{@link #KafkaMQClient(Producer<String, String>)} —— 注入已初始化的原生 Kafka producer（runtime 自动装配用）</li>
 *   <li>{@link #KafkaMQClient(KafkaMQProperties)} —— 自行根据 properties 构造 producer</li>
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
     * KafkaMQProperties 用于懒构造
     */
    private final KafkaMQProperties properties;
    /**
     * 已注入或懒构造的 Kafka producer
     */
    private Producer<String, String> producer;
    /** \u7528\u4e8e\u63a5\u6536 Kafka \u53d1\u5e03\u5b8c\u6210\u901a\u77e5\u7684\u56de\u8c03\u3002 */
    private Callback callback;

    /**
     * \u6784\u9020\u65b9\u6cd5 1\uff1a\u6ce8\u5165\u539f\u751f producer\uff08runtime \u81ea\u52a8\u88c5\u914d\u7528\uff09\u3002
     *
     * @param producer \u539f\u751f Kafka producer
     * @param callback \u53d1\u5e03\u56de\u8c03\uff0c\u53ef\u4e3a null
     */
    public KafkaMQClient(Producer<String, String> producer, Callback callback) {
        this.properties = null;
        this.producer = Objects.requireNonNull(producer, "KafkaMQ Producer is required");
        this.callback = callback;
    }

    /**
     * \u6784\u9020\u65b9\u6cd5 2\uff1a\u81ea\u884c\u6839\u636e properties \u6784\u9020 producer\uff08lazy\uff09\u3002
     *
     * @param properties Kafka \u5ba2\u6237\u7aef\u914d\u7f6e
     * @param callback \u53d1\u5e03\u56de\u8c03\uff0c\u53ef\u4e3a null
     */
    public KafkaMQClient(KafkaMQProperties properties, Callback callback) {
        this.properties = Objects.requireNonNull(properties, "KafkaMQ Properties is required");
        this.producer = null;
        this.callback = callback;
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
     * Kafka topic \u7528 {@code "_"} \u62fc\u63a5 namespace\uff08\u4e0e ActiveMQ \u7684 {@code "."} \u533a\u5206\uff09\u3002
     *
     * @return \u9ed8\u8ba4\u7684\u4e3b\u9898\u540d\u8fde\u63a5\u5b57\u7b26\u4e32
     */
    @Override
    public String defaultConcat() {
        return "_";
    }

    // ========================= 生产者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>Kafka 实现调用 <code>properties.producerProperties()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        if (Objects.isNull(producer) && Objects.nonNull(this.properties)) {
            this.producer = new KafkaProducer<>(properties.producerProperties());
            // 启动时通过 AdminClient 确保 topic 存在
            if (properties.isAutoCreateTopics()) {
                ensureTopic(mqProperties.getNamespace(), mqProperties.getDefaultTopic());
            }
        }
        Producer<String, String> producer1 = this.producer;
        return mqEvent -> {
            String payload = serialization().serialize(mqEvent);
            String topic = resolveTopic(mqEvent, mqProperties);
            String key = partitionKey(mqEvent);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
            if (Objects.nonNull(mqEvent.getMsgId())) {
                record.headers().add(MessageHeaders.HEADER_MESSAGE_ID,
                        mqEvent.getMsgId().getBytes(StandardCharsets.UTF_8));
            }
            // 异步 send 回调（非阻塞发布、统一 ack/nack 收口）
            producer1.send(record, Objects.nonNull(callback) ? callback : new SendCallback(topic, payload));
            log.info("Publish MQ [{}]: {}", topic, payload);
        };
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

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>Kafka 实现调用 <code>properties.consumerProperties(buildGroupId(mqListener))</code>。</p>
     *
     * @param mqListener 传给 <code>properties.consumerProperties(buildGroupId(mqListener))</code>
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 消费者完成注册时为真
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener mqListener, MQProperties mqProperties) throws Exception {
        if (Objects.isNull(properties)) {
            return false;
        }
        Properties props = properties.consumerProperties(buildGroupId(mqListener));
        props.put("bootstrap.servers", properties.getBootstrapServers());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(resolveTopic(mqListener, mqProperties)));
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
                    if (Objects.isNull(mqEvent)) {
                        consumer.commitSync();
                        log.warn("Consume MQ [{}] failed: the mqEvent is null", mqListener.getRouteExpression(this.defaultConcat()));
                        continue;
                    }
                    if (!TagMatcher.match(mqEvent.getTag(), mqListener.getTags())) {
                        continue;
                    }
                    KafkaMessageAcknowledgment ack = new KafkaMessageAcknowledgment(consumer, record);
                    try {
                        consume(mqListener, mqEvent, ack);
                        if (!ack.isAcknowledged() && !mqProperties.isAutoAck()) {
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

    // ========================= 消费者 =========================

    /**
     * \u6784\u9020 consumer group.id\uff08\u515c\u5e95\uff09\u3002
     *
     * @param listener \u63a5\u6536\u6d88\u606f\u7684\u76d1\u542c\u5668\u5b9a\u4e49
     * @return Kafka \u6d88\u8d39\u7ec4\u6807\u8bc6
     */
    private String buildGroupId(MQListener listener) {
        return StrKit.isNotEmpty(listener.getGroup())
                ? listener.getGroup()
                : "ddd4j-" + listener.getMethod().getName();
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
}
