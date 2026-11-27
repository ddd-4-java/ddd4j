package io.ddd4j.mq.redisstream;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.resps.StreamGroupInfo;

import java.util.*;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Redis Stream 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>100% 对齐 base-mq {@code RedisStreamClient} 风格：仅 3 个公开方法
 * {@link #impl()} / {@link #initProducer} / {@link #initConsumer}，
 * 逻辑全部内联，守护线程循环。
 *
 * <ul>
 *   <li>{@link #initProducer} —— {@link BlockingQueue} + 守护线程，循环 take 后 {@code jedis.xadd(streamKey, payload)}，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— {@code xgroupCreate}（如不存在）+ 守护线程 {@code xreadGroup} 轮询，
 *       每条消息反序列化、构建 {@link RedisStreamAcknowledgment} 后调 {@link #consume} 统一消费，
 *       autoAck=false 时手动 {@code xack}</li>
 * </ul>
 *
 * <p>Stream key 通过 {@link MQClient#resolveTopic(MQEvent, MQProperties)} /
 * {@link MQClient#resolveTopic(MQListener, MQProperties)} 解析；
 * 默认拼接符 {@code :}（Redis Stream 习惯），可由 {@link #defaultConcat()} 覆写决定。
 * tag header 通过 {@link #tagHeaderKey()}（默认 {@code "ddd4jTag"}）写入 stream 字段，
 * 消费者读取同 key 走应用层 {@link TagMatcher#match} 过滤（Redis Stream 无 broker 端 selector）。
 *
 * <p>注：构造方法 1 接收 {@link UnifiedJedis} 而非旧版 {@code Jedis}——Jedis 7.x 已将
 * {@code Jedis} 与 {@code UnifiedJedis} 拆为兄弟接口，{@link RedisStreamAcknowledgment}
 * 的契约需要 {@code UnifiedJedis}，故统一走该类型（{@code JedisPooled} / 任何标准 Jedis 7.x 客户端均可注入）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : redisStreamMQClient ###")
public class RedisStreamMQClient implements MQClient {

    /** 保存 <code>started</code> 对应的 <code>AtomicBoolean</code> 数据。 */
    private final AtomicBoolean started = new AtomicBoolean(false);
    /**
     * 双构造：构造方法 1 直接持有外部注入的 Jedis 客户端（UnifiedJedis 形态）。
     */
    private final UnifiedJedis injectedJedis;
    /**
     * 双构造：构造方法 2 持有 properties，第一次调用时 lazy 构造 Jedis。
     */
    private final RedisStreamMQProperties properties;
    /** 保存 <code>SENDING MSGS</code> 对应的 <code>BlockingQueue&lt;MQEvent&gt;</code> 数据。 */
    BlockingQueue<MQEvent> SENDING_MSGS = new LinkedBlockingQueue<>();
    /**
     * lazy 构造的 Jedis（volatile 保证发布可见性）。
     */
    private volatile UnifiedJedis lazyJedis;

    /**
     * \u4f7f\u7528\u5df2\u6709 Jedis \u5ba2\u6237\u7aef\u521b\u5efa Stream \u6d88\u606f\u5ba2\u6237\u7aef\u3002
     * @param jedis Jedis \u5ba2\u6237\u7aef
     */
    public RedisStreamMQClient(UnifiedJedis jedis) {
        this.injectedJedis = jedis;
        this.properties = null;
    }

    /**
     * \u6839\u636e\u8fde\u63a5\u914d\u7f6e\u521b\u5efa Stream \u6d88\u606f\u5ba2\u6237\u7aef\u3002
     * @param properties Redis Stream \u914d\u7f6e
     */
    public RedisStreamMQClient(RedisStreamMQProperties properties) {
        this.injectedJedis = null;
        this.properties = properties;
    }

    /**
     * \u83b7\u53d6\u5f53\u524d\u5b9e\u4f8b\u4f7f\u7528\u7684 Jedis \u5ba2\u6237\u7aef\uff08\u6ce8\u5165\u4f18\u5148\uff0c\u5426\u5219 lazy \u6784\u9020\uff09\u3002
     *
     * @return \u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684 Jedis \u5ba2\u6237\u7aef
     */
    private UnifiedJedis jedis() {
        if (Objects.nonNull(injectedJedis)) {
            return injectedJedis;
        }
        UnifiedJedis j = lazyJedis;
        if (Objects.isNull(j)) {
            synchronized (this) {
                j = lazyJedis;
                if (Objects.isNull(j)) {
                    j = properties.newJedis();
                    lazyJedis = j;
                }
            }
        }
        return j;
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"redisStream"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "redisStream";
    }

    /**
     * Redis Stream \u9ed8\u8ba4\u62fc\u63a5\u7b26 {@code :}\uff08Redis \u547d\u540d\u4e60\u60ef\uff09\u3002
     *
     * @return \u9ed8\u8ba4\u7684\u6d41\u540d\u79f0\u8fde\u63a5\u5b57\u7b26\u4e32
     */
    @Override
    public String defaultConcat() {
        return ":";
    }

    // ========================= 生产者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>此实现调用 <code>started.compareAndSet(false, true)</code>。</p>
     *
     * @param properties MQ 配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties properties) {
        if (started.compareAndSet(false, true)) {
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ddd4j-redis-stream-publisher");
                t.setDaemon(true);
                return t;
            }).submit(() -> {
                log.info("MQ publisher start");
                while (!Thread.currentThread().isInterrupted()) {
                    String payload = null;
                    String streamKey = null;
                    try {
                        MQEvent mqEvent = SENDING_MSGS.take();
                        payload = serialization().serialize(mqEvent).toString();
                        streamKey = resolveTopic(mqEvent, properties);
                        Map<String, String> fields = new HashMap<>();
                        fields.put("payload", payload);
                        if (StrKit.isNotEmpty(mqEvent.getMsgId())) {
                            fields.put(MessageHeaders.HEADER_MESSAGE_ID, mqEvent.getMsgId());
                        }
                        if (Objects.nonNull(mqEvent.getTag())) {
                            // tag header 走 tagHeaderKey()（与 selector/consumer 读对齐）
                            fields.put(tagHeaderKey(), mqEvent.getTag());
                        }
                        jedis().xadd(streamKey, StreamEntryID.NEW_ENTRY, fields);
                        log.info("Publish MQ [{}]: {}", streamKey, payload);
                    } catch (Exception e) {
                        log.error("Publish MQ [{}]: {} failed!", streamKey, payload, e);
                    }
                }
                log.info("MQ publisher stopped");
            });
        }
        return mqEvent -> SENDING_MSGS.offer(mqEvent);
    }

    // ========================= 消费者 =========================

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>resolveTopic(listener, properties)</code>。</p>
     *
     * @param listener 监听器定义
     * @param properties MQ 配置
     * @return 消费者完成注册时为真
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties properties) throws Exception {
        // 订阅 streamKey=namespace:topic[:tag]（应用层过滤全部正向 tag，故多 stream 各订阅一份）
        List<String> topics = new ArrayList<>();
        String mainTopic = resolveTopic(listener, properties);
        topics.add(mainTopic);
        if (StrKit.isNotEmpty(listener.getTags())) {
            Set<String> tags = TagMatcher.findIncludes(listener.getTags());
            for (String tag : tags) {
                String t = resolveTopic(namespace(listener.getNamespace(), properties),
                        listener.getTopic(), tag, concat(null));
                if (!topics.contains(t)) {
                    topics.add(t);
                }
            }
        }

        Map<String, StreamEntryID> streamKeys = new HashMap<>();
        try {
            UnifiedJedis jedis = jedis();
            // 创建消费组（忽略已存在）
            for (String topic : topics) {
                if (!jedis.exists(topic)) {
                    Map<String, String> empty = new HashMap<>();
                    empty.put("payload", "{}");
                    jedis.xadd(topic, StreamEntryID.NEW_ENTRY, empty);
                }
                List<StreamGroupInfo> streamGroupInfos = jedis.xinfoGroups(topic);
                List<String> streamGroupInfoGroupNames = streamGroupInfos.stream().map(StreamGroupInfo::getName)
                        .collect(Collectors.toList());
                if (!streamGroupInfoGroupNames.contains(listener.getGroup())) {
                    jedis.xgroupCreate(topic, listener.getGroup(), new StreamEntryID("0-0"), true);
                }
                streamKeys.put(topic, StreamEntryID.UNRECEIVED_ENTRY);
            }
        } catch (Exception e) {
            log.error("Create consumer group failed!", e);
            return false;
        }

        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ddd4j-redis-stream-" + listener.getRouteExpression(this.defaultConcat()));
            t.setDaemon(true);
            return t;
        }).submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    UnifiedJedis jedis = jedis();
                    // 从消费组中读取消息
                    List<Map.Entry<String, List<StreamEntry>>> messages = jedis.xreadGroup(listener.getGroup(),
                            listener.getMethod().getName(),
                            XReadGroupParams.xReadGroupParams().count(10).block(1000),
                            streamKeys);
                    if (Objects.isNull(messages) || messages.isEmpty()) {
                        // 没有消息，短暂休眠，避免CPU占用过高
                        TimeUnit.MILLISECONDS.sleep(1000);
                        continue;
                    }

                    for (Map.Entry<String, List<StreamEntry>> entry : messages) {
                        for (StreamEntry streamEntry : entry.getValue()) {
                            String payload = streamEntry.getFields().get("payload");
                            String tag = streamEntry.getFields().get(tagHeaderKey());
                            if (!TagMatcher.match(tag, listener.getTags())) {
                                jedis.xack(entry.getKey(), listener.getGroup(), streamEntry.getID());
                                continue;
                            }
                            MQEvent mqEvent = serialization().deserialize(payload, listener.payloadType());
                            if (Objects.isNull(mqEvent)) {
                                jedis.xack(entry.getKey(), listener.getGroup(), streamEntry.getID());
                                log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                                continue;
                            }
                            String messageId = messageId(streamEntry.getFields());
                            if (StrKit.isNotEmpty(messageId)) {
                                mqEvent.setMsgId(messageId);
                            }
                            Acknowledgment ack = new RedisStreamAcknowledgment(
                                    jedis, entry.getKey(), listener.getGroup(), streamEntry.getID(),
                                    mqEvent.getMsgId(), mqEvent.getMsgId());
                            try {
                                if (properties.isAutoAck()) {
                                    jedis.xack(entry.getKey(), listener.getGroup(), streamEntry.getID());
                                }
                                // 消费消息
                                consume(listener, mqEvent, ack);
                                // 确认消息
                                if (!properties.isAutoAck() && !ack.isAcknowledged()) {
                                    jedis.xack(entry.getKey(), listener.getGroup(), streamEntry.getID());
                                }
                            } catch (Throwable e) {
                                log.error("Consume MQ [{}] failed: {}", listener.getRouteExpression(this.defaultConcat()), payload, e);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Consume MQ [{}] failed!", listener.getRouteExpression(this.defaultConcat()), e);
                    try {
                        TimeUnit.MILLISECONDS.sleep(5000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        return true;
    }

    /**
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param fields \u5f53\u524d Redis Stream \u6761\u76ee\u7684\u5b57\u6bb5\u6620\u5c04
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(Map<String, String> fields) {
        String messageId = fields.get(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : fields.get(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

}
