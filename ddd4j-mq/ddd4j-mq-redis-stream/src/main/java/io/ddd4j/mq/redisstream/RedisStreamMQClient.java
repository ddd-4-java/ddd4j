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
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private final List<ExecutorService> consumerExecutors = new CopyOnWriteArrayList<>();
    private final List<UnifiedJedis> ownedConsumerClients = new CopyOnWriteArrayList<>();

    /**
     * 双构造：构造方法 1 直接持有外部注入的 Jedis 客户端（UnifiedJedis 形态）。
     */
    private final UnifiedJedis injectedJedis;
    /**
     * 双构造：构造方法 2 持有 properties，第一次调用时 lazy 构造 Jedis。
     */
    private final RedisStreamMQProperties properties;
    /**
     * lazy 构造的 Jedis（volatile 保证发布可见性）。
     */
    private volatile UnifiedJedis lazyJedis;

    public RedisStreamMQClient(UnifiedJedis jedis) {
        this.injectedJedis = jedis;
        this.properties = null;
    }

    public RedisStreamMQClient(RedisStreamMQProperties properties) {
        this.injectedJedis = null;
        this.properties = properties;
    }

    /**
     * 获取当前实例使用的 Jedis 客户端（注入优先，否则 lazy 构造）。
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

    @Override
    public String impl() {
        return "redisStream";
    }

    /**
     * Redis Stream 默认拼接符 {@code :}（Redis 命名习惯）。
     */
    @Override
    public String defaultConcat() {
        return ":";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties properties) {
        return mqEvent -> {
            String payload = serialization().serialize(mqEvent).toString();
            String streamKey = resolveTopic(mqEvent, properties);
            Map<String, String> fields = new HashMap<>();
            fields.put("payload", payload);
            if (StrKit.isNotEmpty(mqEvent.getMsgId())) {
                fields.put(MessageHeaders.HEADER_MESSAGE_ID, mqEvent.getMsgId());
            }
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

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties properties) throws Exception {
        final UnifiedJedis consumerJedis;
        if (Objects.nonNull(this.properties)) {
            consumerJedis = this.properties.newJedis();
            ownedConsumerClients.add(consumerJedis);
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
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    UnifiedJedis jedis = consumerJedis;
                    // 稳定 consumer 重启后先恢复自己的 PEL，再读取尚未投递的新消息。
                    List<Map.Entry<String, List<StreamEntry>>> messages = jedis.xreadGroup(listener.getGroup(),
                            listener.getMethod().getName(),
                            XReadGroupParams.xReadGroupParams().count(10), pendingKeys);
                    if (!hasEntries(messages)) {
                        messages = jedis.xreadGroup(listener.getGroup(), listener.getMethod().getName(),
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
                            Acknowledgment ack = new RedisStreamAcknowledgment(
                                    jedis, entry.getKey(), listener.getGroup(), streamEntry.getID(),
                                    mqEvent.getMsgId(), mqEvent.getMsgId());
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

    private static boolean hasEntries(List<Map.Entry<String, List<StreamEntry>>> messages) {
        return Objects.nonNull(messages) && messages.stream()
                .anyMatch(entry -> Objects.nonNull(entry.getValue()) && !entry.getValue().isEmpty());
    }

    @Override
    public void close() {
        for (ExecutorService executor : consumerExecutors) {
            executor.shutdownNow();
        }
        consumerExecutors.clear();
        for (UnifiedJedis client : ownedConsumerClients) {
            client.close();
        }
        ownedConsumerClients.clear();
        if (Objects.nonNull(lazyJedis)) {
            lazyJedis.close();
            lazyJedis = null;
        }
    }

    static String messageId(Map<String, String> fields) {
        String messageId = fields.get(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : fields.get(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

}
