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
package io.ddd4j.mq.redisstream;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.XAutoClaimParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.resps.StreamGroupInfo;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 *   <li>{@link #initProducer} —— 同步执行 {@code jedis.xadd(streamKey, payload)}，
 *       仅在 Redis 确认写入后向调用方返回</li>
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

    /** \u8fd0\u884c Redis Stream \u8ba2\u9605\u8bfb\u53d6\u5faa\u73af\u7684\u6267\u884c\u5668\u96c6\u5408\u3002 */
    private final List<ExecutorService> consumerExecutors = new CopyOnWriteArrayList<>();
    /** \u6ce8\u518c\u5de5\u4f5c\u5faa\u73af\u65f6\u7531\u5f53\u524d\u9002\u914d\u5668\u62e5\u6709\u7684\u6d88\u8d39\u8005\u8fde\u63a5\u3002 */
    private final List<UnifiedJedis> ownedConsumerClients = new CopyOnWriteArrayList<>();

    /**
     * 双构造：构造方法 1 直接持有外部注入的 Jedis 客户端（UnifiedJedis 形态）。
     */
    private final UnifiedJedis injectedJedis;
    /**
     * 双构造：构造方法 2 持有 properties，第一次调用时 lazy 构造 Jedis。
     */
    private final RedisStreamMQProperties properties;
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("redisStream");
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
     * \u68c0\u67e5 Redis Stream \u8bfb\u53d6\u7ed3\u679c\u662f\u5426\u5305\u542b\u81f3\u5c11\u4e00\u6761\u6d88\u606f\u3002
     * @param messages Redis \u8fd4\u56de\u7684\u6309 Stream \u5206\u7ec4\u7684\u8bfb\u53d6\u7ed3\u679c
     * @return \u5b58\u5728\u53ef\u5904\u7406\u6761\u76ee\u65f6\u8fd4\u56de true
     */
    private static boolean hasEntries(List<Map.Entry<String, List<StreamEntry>>> messages) {
        return Objects.nonNull(messages) && messages.stream()
                .anyMatch(entry -> Objects.nonNull(entry.getValue()) && !entry.getValue().isEmpty());
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
                    UnifiedJedis ownedJedis = j;
                    lifecycle.register("redis-stream-client", ownedJedis::close);
                }
            }
        }
        return j;
    }

    /** {@inheritDoc} */
    @Override
    public String impl() {
        return "redisStream";
    }

    /** {@inheritDoc} */
    @Override
    public MQClientLifecycle lifecycle() {
        return lifecycle;
    }

    // ========================= 生产者 =========================

    /** {@inheritDoc} */
    @Override
    public MQStartupStatus startupStatus() {
        return startupStatus;
    }

    // ========================= 消费者 =========================

    /**
     * Redis Stream \u9ed8\u8ba4\u62fc\u63a5\u7b26 {@code :}\uff08Redis \u547d\u540d\u4e60\u60ef\uff09\u3002
     *
     * @return \u9ed8\u8ba4\u7684\u6d41\u540d\u79f0\u8fde\u63a5\u5b57\u7b26\u4e32
     */
    @Override
    public String defaultConcat() {
        return ":";
    }

    /** {@inheritDoc} */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties properties) {
        return mqEvent -> {
            String payload = serialization().serialize(mqEvent).toString();
            String streamKey = resolveTopic(mqEvent, properties);
            Map<String, String> fields = new HashMap<>();
            fields.put("payload", payload);
            fields.putAll(mqEvent.headerMap());
            if (Objects.nonNull(mqEvent.getTag())) {
                fields.put(tagHeaderKey(), mqEvent.getTag());
            }
            try {
                jedis().xadd(streamKey, StreamEntryID.NEW_ENTRY, fields);
                log.info("Publish MQ [{}]: {}", streamKey, payload);
            } catch (Exception exception) {
                log.error("Publish MQ [{}]: {} failed!", streamKey, payload, exception);
                throw new IllegalStateException("Publish Redis Stream event failed: " + mqEvent.getMsgId(), exception);
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties properties) throws Exception {
        RedisStreamMQProperties streamProperties = this.properties;
        if (Objects.isNull(streamProperties) && properties instanceof RedisStreamMQProperties) {
            streamProperties = (RedisStreamMQProperties) properties;
        }
        final RedisStreamMQProperties consumerSettings = streamProperties;
        String configuredConsumerName = Objects.nonNull(streamProperties)
                ? streamProperties.getConsumerName() : null;
        String consumerName = StrKit.isNotEmpty(configuredConsumerName)
                && !"ddd4j".equals(configuredConsumerName)
                ? configuredConsumerName + "-" + listener.getMethod().getName()
                : listener.getMethod().getName();
        final UnifiedJedis consumerJedis;
        if (Objects.nonNull(this.properties)) {
            consumerJedis = this.properties.newJedis();
            ownedConsumerClients.add(consumerJedis);
            lifecycle.register("redis-stream-consumer-client", consumerJedis::close);
        } else {
            consumerJedis = jedis();
        }
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
        Map<String, StreamEntryID> pendingKeys = new HashMap<>();
        Map<String, StreamEntryID> claimCursors = new HashMap<>();
        try {
            UnifiedJedis jedis = consumerJedis;
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
                pendingKeys.put(topic, new StreamEntryID("0-0"));
                claimCursors.put(topic, new StreamEntryID("0-0"));
            }
        } catch (Exception e) {
            log.error("Create consumer group failed!", e);
            return false;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ddd4j-redis-stream-" + listener.getRouteExpression(this.defaultConcat()));
            t.setDaemon(true);
            return t;
        });
        consumerExecutors.add(executor);
        lifecycle.register("redis-stream-consumer-executor", executor::shutdownNow);
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    UnifiedJedis jedis = consumerJedis;
                    // 稳定 consumer 重启后先恢复自己的 PEL，再读取尚未投递的新消息。
                    List<Map.Entry<String, List<StreamEntry>>> messages = jedis.xreadGroup(listener.getGroup(),
                            consumerName,
                            XReadGroupParams.xReadGroupParams().count(10), pendingKeys);
                    if (!hasEntries(messages) && Objects.nonNull(consumerSettings)
                            && consumerSettings.isAutoClaimEnabled()) {
                        List<Map.Entry<String, List<StreamEntry>>> claimedMessages = new ArrayList<>();
                        for (String topic : topics) {
                            Map.Entry<StreamEntryID, List<StreamEntry>> claimed = jedis.xautoclaim(
                                    topic, listener.getGroup(), consumerName,
                                    consumerSettings.getClaimMinIdleMillis(), claimCursors.get(topic),
                                    XAutoClaimParams.xAutoClaimParams()
                                            .count(Math.max(1, consumerSettings.getCount())));
                            claimCursors.put(topic, claimed.getKey());
                            if (Objects.nonNull(claimed.getValue()) && !claimed.getValue().isEmpty()) {
                                claimedMessages.add(new AbstractMap.SimpleEntry<>(topic, claimed.getValue()));
                            }
                        }
                        messages = claimedMessages;
                    }
                    if (!hasEntries(messages)) {
                        messages = jedis.xreadGroup(listener.getGroup(), consumerName,
                                XReadGroupParams.xReadGroupParams().count(10).block(1000), streamKeys);
                    }
                    if (!hasEntries(messages)) {
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
                            String correlationId = streamEntry.getFields().get(MQDeliveryHeaders.CORRELATION_ID);
                            if (StrKit.isNotEmpty(correlationId)) {
                                mqEvent.setCorrelationId(correlationId);
                            }
                            String causationId = streamEntry.getFields().get(MQDeliveryHeaders.CAUSATION_ID);
                            if (StrKit.isNotEmpty(causationId)) {
                                mqEvent.setCausationId(causationId);
                            }
                            Acknowledgment ack = new RedisStreamAcknowledgment(
                                    jedis, entry.getKey(), listener.getGroup(), streamEntry.getID(),
                                    mqEvent.getMsgId(), mqEvent.getCorrelationId());
                            try {
                                // 消费消息
                                consume(listener, mqEvent, ack);
                                // 仅在持久化与业务处理均成功后确认；autoAck 不得早于处理。
                                if (!ack.isAcknowledged()) {
                                    ack.ackSingle();
                                }
                            } catch (Throwable e) {
                                log.error("Consume MQ [{}] failed: {}", listener.getRouteExpression(this.defaultConcat()), payload, e);
                            }
                        }
                    }
                } catch (Throwable e) {
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

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
    @Override
    public void close() {
        try {
            lifecycle.close();
        } finally {
            consumerExecutors.clear();
            ownedConsumerClients.clear();
            lazyJedis = null;
            startupStatus.stopped();
        }
    }

}
