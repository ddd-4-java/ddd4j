package io.ddd4j.mq.pulsar;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.api.TypedMessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Apache Pulsar 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer} —— 创建 Pulsar {@link Producer}（按 topic 缓存），返回 {@link Consumer}，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— 创建 Pulsar 消费者，tag 过滤后调 {@link #consume} 统一消费，
 *       传入 {@link PulsarAcknowledgment} 实现不同级别 ack</li>
 * </ul>
 *
 * <p>Pulsar 物理 topic 格式 {@code tenant/namespace/topic[:tag]}，由 {@link PulsarProperties#physicalTopic} 构造。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class PulsarClient implements MQClient {

    private final PulsarProperties properties;
    private final List<org.apache.pulsar.client.api.Consumer<?>> consumers = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, Producer<byte[]>> producers = new ConcurrentHashMap<>();
    private org.apache.pulsar.client.api.PulsarClient client;

    public PulsarClient(PulsarProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "pulsar";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            this.client = properties.client();
            return this::publish;
        } catch (Exception ex) {
            throw new IllegalStateException("Init Pulsar producer failed", ex);
        }
    }

    private void publish(MQEvent event) {
        try {
            String topic = StrKit.hasText(event.getTopic()) ? event.getTopic() : "ddd4j.default.topic";
            String tag = event.getTag();
            String physical = properties.physicalTopic(topic, tag);
            Producer<byte[]> p = producer(physical);
            TypedMessageBuilder<byte[]> builder = p.newMessage()
                    .value(serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8))
                    .property(MessageHeaders.HEADER_DESTINATION_TOPIC, topic);
            if (Objects.nonNull(event.getMsgId())) {
                builder.property(MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
            }
            if (Objects.nonNull(event.getTenantId())) {
                builder.property(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
            }
            if (Objects.nonNull(event.getTag())) {
                builder.property(MessageHeaders.HEADER_DESTINATION_TAG, event.getTag());
            }
            builder.sendAsync();
            logger().info("Publish MQ [{}]: {}", physical, serialization().serialize(event));
        } catch (Exception ex) {
            throw new IllegalStateException("Publish Pulsar event failed", ex);
        }
    }

    private Producer<byte[]> producer(String topic) throws Exception {
        return producers.computeIfAbsent(topic, t -> {
            try {
                return client.newProducer(Schema.BYTES)
                        .topic(t)
                        .batchingMaxPublishDelay(10, TimeUnit.MILLISECONDS)
                        .create();
            } catch (Exception ex) {
                throw new IllegalStateException("Create Pulsar producer failed: " + t, ex);
            }
        });
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        Objects.requireNonNull(listener, "listener");
        String topic = properties.physicalTopic(
                StrKit.hasText(listener.getTopic()) ? listener.getTopic() : "ddd4j.default.topic",
                null);
        String subscriptionName = properties.getSubscriptionName() + "-" + listener.namespaceTopicTags();
        org.apache.pulsar.client.api.Consumer<byte[]> consumer = client.newConsumer(Schema.BYTES)
                .topic(topic)
                .subscriptionName(subscriptionName)
                .subscriptionType(SubscriptionType.valueOf(properties.getSubscriptionType()))
                .negativeAckRedeliveryDelay(properties.getNegativeAckRedeliveryDelayMs(), TimeUnit.MILLISECONDS)
                .messageListener((c, msg) -> handleMessage(c, msg, listener))
                .subscribe();
        consumers.add(consumer);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void handleMessage(org.apache.pulsar.client.api.Consumer<byte[]> consumer, Message<byte[]> msg, MQListener listener) {
        try {
            String tag = msg.getProperty(MessageHeaders.HEADER_DESTINATION_TAG);
            if (!TagMatcher.match(tag, listener.getTags())) {
                ((org.apache.pulsar.client.api.Consumer) consumer).acknowledge(msg);
                return;
            }
            String messageId = messageIdString(msg.getMessageId());
            String payload = new String(msg.getValue(), StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                ((org.apache.pulsar.client.api.Consumer) consumer).acknowledge(msg);
                return;
            }
            // 同步 tag/msgId 字段
            if (Objects.nonNull(tag)) {
                event.setTag(tag);
            }
            if (Objects.nonNull(messageId)) {
                event.setMsgId(messageId);
            }
            PulsarAcknowledgment ack = new PulsarAcknowledgment(consumer, msg, messageId, null);
            try {
                consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
                try {
                    ((org.apache.pulsar.client.api.Consumer) consumer).negativeAcknowledge(msg);
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
            try {
                ((org.apache.pulsar.client.api.Consumer) consumer).negativeAcknowledge(msg);
            } catch (Exception ignore) {
            }
        }
    }

    private static String messageIdString(MessageId id) {
        return Objects.isNull(id) ? null : id.toString();
    }

    @Override
    public void start() {
        // Pulsar consumer 在 .subscribe() 时已启动
    }

    // ========================= 关闭 =========================

    public void close() throws Exception {
        for (org.apache.pulsar.client.api.Consumer<?> c : new ArrayList<>(consumers)) {
            try {
                c.close();
            } catch (Exception ex) {
                logger().warn("Close Pulsar consumer failed", ex);
            }
        }
        consumers.clear();
        for (Producer<byte[]> p : producers.values()) {
            try {
                p.close();
            } catch (Exception ex) {
                logger().warn("Close Pulsar producer failed", ex);
            }
        }
        producers.clear();
        if (Objects.nonNull(client)) {
            client.close();
        }
    }
}
