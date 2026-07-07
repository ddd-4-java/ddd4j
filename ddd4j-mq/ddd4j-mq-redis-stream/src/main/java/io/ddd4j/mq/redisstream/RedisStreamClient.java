package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Redis Stream 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>整合发布与消费到单一 {@link MQClient} 实现，复用 {@link RedisStreamOperations} SPI
 * 屏蔽 Jedis / Lettuce / Redisson 三种客户端差异：
 * <ul>
 *   <li>{@link #initProducer} —— {@link BlockingQueue} + 守护线程，循环 take 后 {@code xadd}，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— {@code xgroupCreate}（如不存在）+ 守护线程 {@code xreadGroup} 轮询，
 *       每条消息反序列化、构建 {@link RedisStreamAcknowledgment} 后调 {@link #consume} 统一消费</li>
 * </ul>
 *
 * <p>Stream key = {@code namespace:topic[:tag]}，分隔符 {@code :}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class RedisStreamClient implements MQClient {

    public static final String FIELD_PAYLOAD = "payload";

    private final RedisStreamMQProperties properties;
    private final BlockingQueue<MQEvent> sendingQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean started = new AtomicBoolean(false);

    public RedisStreamClient(RedisStreamMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "redisStream";
    }

    // ========================= 发布 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        if (started.compareAndSet(false, true)) {
            startPublisherThread(mqProperties);
        }
        return sendingQueue::offer;
    }

    private void startPublisherThread(MQProperties mqProperties) {
        RedisStreamOperations operations = properties.newOperations();
        Thread thread = new Thread(() -> publisherLoop(operations, mqProperties), "ddd4j-redis-stream-publisher");
        thread.setDaemon(true);
        thread.start();
    }

    private void publisherLoop(RedisStreamOperations operations, MQProperties mqProperties) {
        logger().info("MQ publisher start");
        while (!Thread.currentThread().isInterrupted()) {
            String streamKey = null;
            String payload = null;
            try {
                MQEvent event = sendingQueue.take();
                streamKey = resolveStreamKey(event);
                payload = serialization().serialize(event).toString();
                operations.add(streamKey, Collections.singletonMap(FIELD_PAYLOAD, payload));
                logger().info("Publish MQ [{}]: {}", streamKey, payload);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                logger().error("Publish MQ [{}]: {} failed!", streamKey, payload, ex);
            }
        }
        logger().info("MQ publisher stopped");
    }

    // ========================= 消费 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        Set<String> streams = resolveStreams(listener);
        String group = resolveGroup(listener);
        RedisStreamOperations operations = properties.newOperations();
        if (properties.isAutoCreateGroup()) {
            streams.forEach(stream -> operations.createGroup(stream, group));
        }
        Thread thread = new Thread(() -> consumerLoop(operations, listener, group, streams, mqProperties),
                "ddd4j-redis-stream-" + listener.namespaceTopicTags());
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private void consumerLoop(RedisStreamOperations operations, MQListener listener,
                              String group, Set<String> streams, MQProperties mqProperties) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Map<String, List<RedisStreamRecord>> records = operations.readGroup(
                        group,
                        properties.getConsumerName(),
                        properties.getCount(),
                        properties.getBlockMillis(),
                        streams);
                if (Objects.isNull(records) || records.isEmpty()) {
                    continue;
                }
                records.forEach((stream, entries) -> {
                    if (Objects.nonNull(entries)) {
                        entries.forEach(entry -> consumeEntry(operations, stream, entry, listener, group, mqProperties));
                    }
                });
            } catch (Exception ex) {
                logger().error("Consume MQ [{}] failed!", listener.namespaceTopicTags(), ex);
                try {
                    TimeUnit.MILLISECONDS.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void consumeEntry(RedisStreamOperations operations, String stream, RedisStreamRecord entry,
                              MQListener listener, String group, MQProperties mqProperties) {
        Map<String, String> fields = entry.fields();
        String tag = fields.get(MessageHeaders.HEADER_DESTINATION_TAG);
        if (!TagMatcher.match(tag, listener.getTags())) {
            operations.ack(stream, group, entry.id());
            return;
        }
        String payload = fields.get(FIELD_PAYLOAD);
        String messageId = fields.get(MessageHeaders.HEADER_MESSAGE_ID);
        String correlationId = fields.get(MessageHeaders.HEADER_CORRELATION_ID);
        try {
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                operations.ack(stream, group, entry.id());
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                return;
            }
            if (Objects.nonNull(messageId)) {
                event.setMsgId(messageId);
            }
            if (Objects.nonNull(tag) && Objects.isNull(event.getTag())) {
                event.setTag(tag);
            }
            Acknowledgment ack = new RedisStreamAcknowledgment(
                    operations, stream, group, entry.id(), entry.nativeMessage(), messageId, correlationId);
            if (mqProperties.isAutoAck()) {
                operations.ack(stream, group, entry.id());
            }
            consume(listener, event, ack);
            if (!mqProperties.isAutoAck() && !ack.isAcknowledged()) {
                operations.ack(stream, group, entry.id());
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed: {}", listener.namespaceTopicTags(), payload, ex);
        }
    }

    // ========================= Stream / group 解析 =========================

    private String resolveStreamKey(MQEvent event) {
        return resolvePhysical(event.getNamespace(), event.getTopic(), event.getTag(), ":");
    }

    /**
     * 解析三段式物理目的地：{@code namespace[sep]topic[sep]tag}。
     *
     * @param namespace 命名空间（可空）
     * @param topic     主题（空时回退默认值）
     * @param tag       标签（可空）
     * @param sep       分隔符（如 {@code :}）
     * @return 物理目的地字符串
     */
    private static String resolvePhysical(String namespace, String topic, String tag, String sep) {
        String base = io.ddd4j.kit.lang.StrKit.hasText(topic) ? topic : "ddd4j.default.topic";
        if (io.ddd4j.kit.lang.StrKit.hasText(namespace)) {
            base = namespace + sep + base;
        }
        return io.ddd4j.kit.lang.StrKit.hasText(tag) ? base + sep + tag : base;
    }

    private Set<String> resolveStreams(MQListener listener) {
        String namespace = io.ddd4j.kit.lang.StrKit.hasText(listener.getNamespace()) ? listener.getNamespace() : "";
        String topic = Objects.isNull(listener.getTopic()) ? "ddd4j.default.topic" : listener.getTopic();
        String base = io.ddd4j.kit.lang.StrKit.hasText(namespace) ? namespace + ":" + topic : topic;
        Set<String> includes = TagMatcher.findIncludes(listener.getTags());
        Set<String> streams = new LinkedHashSet<>();
        if (includes.isEmpty()) {
            streams.add(base);
        } else {
            includes.forEach(tag -> streams.add(base + ":" + tag));
        }
        return streams;
    }

    private String resolveGroup(MQListener listener) {
        String group = listener.getGroup();
        if (Objects.isNull(group) || group.isEmpty()) {
            return "ddd4j";
        }
        return group;
    }
}
