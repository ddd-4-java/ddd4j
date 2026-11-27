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

    /**
     * 当前 Lettuce 连接提供的同步 Redis 命令入口。
     */
    private final RedisCommands<String, String> commands;
    /**
     * 当前操作组件退出时应关闭的原生资源。
     */
    private final AutoCloseable closeTarget;
    /**
     * 当前 Redis 操作绑定的原生客户端或连接。
     */
    private final Object nativeClient;

    /**
     * 从 Redis 地址创建 Lettuce Stream 操作适配器。
     *
     * @param redisUrl Redis 连接地址
     */
    public LettuceRedisStreamOperations(String redisUrl) {
        this(createClient(redisUrl));
    }

    /**
     * 复用已有 Lettuce 同步命令接口。
     *
     * @param commands Lettuce 同步命令接口
     */
    public LettuceRedisStreamOperations(RedisCommands<String, String> commands) {
        this(commands, null, commands);
    }

    /**
     * 复用已有 Lettuce 连接。
     *
     * @param connection Lettuce 连接
     */
    public LettuceRedisStreamOperations(StatefulRedisConnection<String, String> connection) {
        this(connection.sync(), connection, connection);
    }

    /**
     * 装配操作 Redis Stream 时使用的 Lettuce 资源及所有权。
     *
     * @param client 当前使用的原生代理或 Redis 客户端
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
     * 装配操作 Redis Stream 时使用的 Lettuce 资源及所有权。
     *
     * @param commands     当前 Lettuce 连接上的同步命令入口
     * @param closeTarget  关闭当前操作对象时应释放的资源
     * @param nativeClient 供调用者识别或复用的原生客户端对象
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
     * 按给定地址创建当前适配器使用的 Redis 客户端。
     *
     * @param redisUrl Redis 连接地址
     * @return 新建的原生 Redis 客户端
     */
    private static RedisClient createClient(String redisUrl) {
        return RedisClient.create(redisUrl);
    }

    /**
     * 向 Redis Stream 添加消息。
     *
     * <p>Lettuce 实现调用 <code>commands.xadd(stream, new XAddArgs().id("*"), fields)</code>。</p>
     *
     * @param stream Stream 名称
     * @param fields 消息字段
     * @return 实际返回<code>commands.xadd(stream, new XAddArgs().id("*"), fields)</code>
     */
    @Override
    public String add(String stream, Map<String, String> fields) {
        return commands.xadd(stream, new XAddArgs().id("*"), fields);
    }

    /**
     * 创建 Redis Stream 消费者组。
     *
     * <p>Lettuce 实现调用 <code>commands.xgroupCreate(StreamOffset.from(stream, "$"), group, new XGroupCreateArgs().mkstream(true))</code>。</p>
     *
     * @param stream Stream 名称
     * @param group  消费者组名
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
     * @param group       消费者组名
     * @param consumer    消费者名称
     * @param count       每次读取的最大条数
     * @param blockMillis 阻塞等待毫秒数
     * @param streams     要读取的 Stream 集合
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
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>Lettuce 实现调用 <code>commands.xack(stream, group, entryId)</code>。</p>
     *
     * @param stream  Stream 名称
     * @param group   消费者组名
     * @param entryId 消息条目 ID
     */
    @Override
    public void ack(String stream, String group, String entryId) {
        commands.xack(stream, group, entryId);
    }

    /**
     * 返回此适配器持有的原生客户端。
     *
     * @return 底层客户端实例
     */
    @Override
    public Object nativeClient() {
        return nativeClient;
    }

    /**
     * 关闭当前实例持有的 Lettuce 资源，借用状态下无需清理。
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
