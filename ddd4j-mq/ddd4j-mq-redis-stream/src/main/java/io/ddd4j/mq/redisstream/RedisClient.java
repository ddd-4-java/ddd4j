package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.UnifiedJedis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Redis Pub/Sub 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>因 ddd4j 无独立 redis 模块，故与 Redis Stream 共置 {@code ddd4j-mq-redis-stream} 模块，
 * 复用 {@link RedisStreamMQProperties#newJedis()} 获取 {@link UnifiedJedis} 连接。
 *
 * <ul>
 *   <li>{@link #initProducer} —— {@link BlockingQueue} + 守护线程，循环 take 后 {@code jedis.publish(channel, payload)}
 *       ，{@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— 守护线程 {@code jedis.subscribe(JedisPubSub, channels)}，
 *       {@code onMessage} 回调里反序列化后调 {@link #consume(MQListener, MQEvent)}（pubsub 无 ack，传 null）</li>
 * </ul>
 *
 * <p>Channel = {@code namespace:topic[:tag]}，分隔符 {@code :}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class RedisClient implements MQClient {

    private final RedisStreamMQProperties properties;
    private final BlockingQueue<MQEvent> sendingQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean started = new AtomicBoolean(false);

    public RedisClient(RedisStreamMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "redis";
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
        Thread thread = new Thread(() -> publisherLoop(mqProperties), "ddd4j-redis-pubsub-publisher");
        thread.setDaemon(true);
        thread.start();
    }

    private void publisherLoop(MQProperties mqProperties) {
        logger().info("MQ publisher start");
        UnifiedJedis jedis = properties.newJedis();
        while (!Thread.currentThread().isInterrupted()) {
            String channel = null;
            String payload = null;
            try {
                MQEvent event = sendingQueue.take();
                channel = resolveChannel(event);
                payload = serialization().serialize(event).toString();
                jedis.publish(channel, payload);
                logger().info("Publish MQ [{}]: {}", channel, payload);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                logger().error("Publish MQ [{}]: {} failed!", channel, payload, ex);
            }
        }
        logger().info("MQ publisher stopped");
    }

    // ========================= 消费 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        List<String> channels = resolveChannels(listener);
        Thread thread = new Thread(() -> subscribeLoop(listener, channels), "ddd4j-redis-pubsub-" + listener.namespaceTopicTags());
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private void subscribeLoop(MQListener listener, List<String> channels) {
        try {
            UnifiedJedis jedis = properties.newJedis();
            jedis.subscribe(new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    handleMessage(listener, channel, message);
                }

                @Override
                public void onSubscribe(String channel, int subscribedChannels) {
                    logger().info("Subscribed channel: {}", channel);
                }

                @Override
                public void onUnsubscribe(String channel, int subscribedChannels) {
                    logger().info("Unsubscribed channel: {}", channel);
                }
            }, channels.toArray(new String[0]));
        } catch (Exception ex) {
            logger().error("Subscribe MQ [{}] failed!", listener.namespaceTopicTags(), ex);
        }
    }

    private void handleMessage(MQListener listener, String channel, String message) {
        try {
            MQEvent event = serialization().deserialize(message, listener.payloadType());
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                return;
            }
            // pubsub 无 ack 概念
            consume(listener, event, null);
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed: {}", listener.namespaceTopicTags(), message, ex);
        }
    }

    // ========================= Channel 解析 =========================

    private String resolveChannel(MQEvent event) {
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

    private List<String> resolveChannels(MQListener listener) {
        String namespace = io.ddd4j.kit.lang.StrKit.hasText(listener.getNamespace()) ? listener.getNamespace() : "";
        String topic = Objects.isNull(listener.getTopic()) ? "ddd4j.default.topic" : listener.getTopic();
        String base = io.ddd4j.kit.lang.StrKit.hasText(namespace) ? namespace + ":" + topic : topic;
        Set<String> includes = TagMatcher.findIncludes(listener.getTags());
        List<String> channels = new ArrayList<>();
        if (includes.isEmpty()) {
            channels.add(base);
        } else {
            includes.forEach(tag -> channels.add(base + ":" + tag));
        }
        return channels;
    }
}
