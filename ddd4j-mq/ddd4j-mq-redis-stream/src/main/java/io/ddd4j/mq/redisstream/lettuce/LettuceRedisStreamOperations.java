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
package io.ddd4j.mq.redisstream.lettuce;

import io.ddd4j.mq.redisstream.RedisStreamOperations;
import io.ddd4j.mq.redisstream.RedisStreamRecord;
import io.lettuce.core.*;
import io.lettuce.core.XReadArgs.StreamOffset;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.*;

/**
 * 基于 Lettuce 客户端的 Redis Stream 操作实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LettuceRedisStreamOperations implements RedisStreamOperations {

    /** \u5f53\u524d Lettuce \u8fde\u63a5\u63d0\u4f9b\u7684\u540c\u6b65 Redis \u547d\u4ee4\u5165\u53e3\u3002 */
    private final RedisCommands<String, String> commands;
    /** \u5f53\u524d\u64cd\u4f5c\u7ec4\u4ef6\u9000\u51fa\u65f6\u5e94\u5173\u95ed\u7684\u539f\u751f\u8d44\u6e90\u3002 */
    private final AutoCloseable closeTarget;
    /** \u5f53\u524d Redis \u64cd\u4f5c\u7ed1\u5b9a\u7684\u539f\u751f\u5ba2\u6237\u7aef\u6216\u8fde\u63a5\u3002 */
    private final Object nativeClient;

    /**
     * \u4ece Redis \u5730\u5740\u521b\u5efa Lettuce Stream \u64cd\u4f5c\u9002\u914d\u5668\u3002
     * @param redisUrl Redis \u8fde\u63a5\u5730\u5740
     */
    public LettuceRedisStreamOperations(String redisUrl) {
        this(createClient(redisUrl));
    }

    /**
     * \u590d\u7528\u5df2\u6709 Lettuce \u540c\u6b65\u547d\u4ee4\u63a5\u53e3\u3002
     * @param commands Lettuce \u540c\u6b65\u547d\u4ee4\u63a5\u53e3
     */
    public LettuceRedisStreamOperations(RedisCommands<String, String> commands) {
        this(commands, null, commands);
    }

    /**
     * \u590d\u7528\u5df2\u6709 Lettuce \u8fde\u63a5\u3002
     * @param connection Lettuce \u8fde\u63a5
     */
    public LettuceRedisStreamOperations(StatefulRedisConnection<String, String> connection) {
        this(connection.sync(), connection, connection);
    }

    /**
     * \u88c5\u914d\u64cd\u4f5c Redis Stream \u65f6\u4f7f\u7528\u7684 Lettuce \u8d44\u6e90\u53ca\u6240\u6709\u6743\u3002
     * @param client \u5f53\u524d\u4f7f\u7528\u7684\u539f\u751f\u4ee3\u7406\u6216 Redis \u5ba2\u6237\u7aef
     */
    private LettuceRedisStreamOperations(RedisClient client) {
        StatefulRedisConnection<String, String> connection = client.connect();
        this.commands = connection.sync();
        this.closeTarget = () -> {
            connection.close();
            client.shutdown();
        };
        this.nativeClient = client;
    }

    /**
     * \u88c5\u914d\u64cd\u4f5c Redis Stream \u65f6\u4f7f\u7528\u7684 Lettuce \u8d44\u6e90\u53ca\u6240\u6709\u6743\u3002
     * @param commands \u5f53\u524d Lettuce \u8fde\u63a5\u4e0a\u7684\u540c\u6b65\u547d\u4ee4\u5165\u53e3
     * @param closeTarget \u5173\u95ed\u5f53\u524d\u64cd\u4f5c\u5bf9\u8c61\u65f6\u5e94\u91ca\u653e\u7684\u8d44\u6e90
     * @param nativeClient \u4f9b\u8c03\u7528\u8005\u8bc6\u522b\u6216\u590d\u7528\u7684\u539f\u751f\u5ba2\u6237\u7aef\u5bf9\u8c61
     */
    private LettuceRedisStreamOperations(
            RedisCommands<String, String> commands,
            AutoCloseable closeTarget,
            Object nativeClient) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.closeTarget = closeTarget;
        this.nativeClient = Objects.isNull(nativeClient) ? commands : nativeClient;
    }

    /**
     * \u6309\u7ed9\u5b9a\u5730\u5740\u521b\u5efa\u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684 Redis \u5ba2\u6237\u7aef\u3002
     * @param redisUrl Redis \u8fde\u63a5\u5730\u5740
     * @return \u65b0\u5efa\u7684\u539f\u751f Redis \u5ba2\u6237\u7aef
     */
    private static RedisClient createClient(String redisUrl) {
        return RedisClient.create(redisUrl);
    }

    /**
     * add() 方法。
     * @param stream 字符串参数。
     * @param fields 字段列表。
     * @return 结果字符串。
     */
    @Override
    public String add(String stream, Map<String, String> fields) {
        return commands.xadd(stream, new XAddArgs().id("*"), fields);
    }

    /**
     * 创建对象。
     * @param stream 字符串参数。
     * @param group 消费组。
     */
    @Override
    public void createGroup(String stream, String group) {
        try {
            commands.xgroupCreate(
                    StreamOffset.from(stream, "$"),
                    group,
                    new XGroupCreateArgs().mkstream(true));
        } catch (RedisCommandExecutionException ex) {
            if (Objects.isNull(ex.getMessage()) || !ex.getMessage().contains("BUSYGROUP")) {
                throw ex;
            }
        }
    }

    /**
     * 以指定消费者组和消费者身份读取 Redis Stream 消息。
     *
     * <p>Lettuce 实现调用 <code>streams.stream()</code>。</p>
     *
     * @param group 消费者组名
     * @param consumer 消费者名称
     * @param count 每次读取的最大条数
     * @param blockMillis 阻塞等待毫秒数
     * @param streams 要读取的 Stream 集合
     * @return 实际返回<code>result</code>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<RedisStreamRecord>> readGroup(
            String group,
            String consumer,
            int count,
            int blockMillis,
            Set<String> streams) {
        XReadArgs args = new XReadArgs().count(count).block(blockMillis);
        StreamOffset<String>[] offsets = streams.stream()
                .map(StreamOffset::lastConsumed)
                .toArray(StreamOffset[]::new);
        List<StreamMessage<String, String>> records = commands.xreadgroup(Consumer.from(group, consumer), args, offsets);
        Map<String, List<RedisStreamRecord>> result = new HashMap<>();
        if (Objects.isNull(records) || records.isEmpty()) {
            return result;
        }
        for (StreamMessage<String, String> record : records) {
            result.computeIfAbsent(record.getStream(), key -> new ArrayList<>())
                    .add(new RedisStreamRecord(record.getStream(), record.getId(), record.getBody(), record));
        }
        return result;
    }

    /**
     * ack() 方法。
     * @param stream 字符串参数。
     * @param group 消费组。
     * @param entryId 字符串参数。
     */
    @Override
    public void ack(String stream, String group, String entryId) {
        commands.xack(stream, group, entryId);
    }

    /**
     * nativeClient() 方法。
     * @return 结果对象。
     */
    @Override
    public Object nativeClient() {
        return nativeClient;
    }

    /**
     * 关闭并释放资源。
     */
    @Override
    public void close() {
        if (Objects.isNull(closeTarget)) {
            return;
        }
        try {
            closeTarget.close();
        } catch (Exception ex) {
            throw new IllegalStateException("Close Lettuce Redis client failed", ex);
        }
    }
}
