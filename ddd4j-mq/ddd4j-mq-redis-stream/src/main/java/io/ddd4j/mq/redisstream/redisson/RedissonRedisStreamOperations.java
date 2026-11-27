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
package io.ddd4j.mq.redisstream.redisson;

import io.ddd4j.mq.redisstream.RedisStreamOperations;
import io.ddd4j.mq.redisstream.RedisStreamRecord;
import org.redisson.Redisson;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.RedisException;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.*;

/**
 * 基于 Redisson 客户端的 Redis Stream 操作实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedissonRedisStreamOperations implements RedisStreamOperations {

    /**
     * 执行 Redis Stream 操作的 Redisson 客户端。
     */
    private final RedissonClient redisson;
    /**
     * 关闭适配器时是否应关闭当前 Redisson 客户端。
     */
    private final boolean closeClient;

    /**
     * 从 Redis 地址创建 Redisson Stream 操作适配器。
     *
     * @param redisUrl Redis 连接地址
     */
    public RedissonRedisStreamOperations(String redisUrl) {
        this(createClient(redisUrl), true);
    }

    /**
     * 复用已有 Redisson 客户端。
     *
     * @param redisson Redisson 客户端
     */
    public RedissonRedisStreamOperations(RedissonClient redisson) {
        this(redisson, false);
    }

    /**
     * 指定客户端生命周期归属后创建操作适配器。
     *
     * @param redisson    Redisson 客户端
     * @param closeClient 关闭适配器时是否关闭客户端
     */
    public RedissonRedisStreamOperations(RedissonClient redisson, boolean closeClient) {
        this.redisson = Objects.requireNonNull(redisson, "redisson");
        this.closeClient = closeClient;
    }

    /**
     * 按给定地址创建当前适配器使用的 Redis 客户端。
     *
     * @param redisUrl Redis 连接地址
     * @return 新建的原生 Redis 客户端
     */
    private static RedissonClient createClient(String redisUrl) {
        Config config = new Config();
        config.useSingleServer().setAddress(redisUrl);
        return Redisson.create(config);
    }

    /**
     * 将时间部分和序号部分组成的文本转换为 Redisson Stream 标识。
     *
     * @param entryId Redis Stream 条目的文本标识
     * @return 对应的 Redisson 消息标识
     */
    private static StreamMessageId messageId(String entryId) {
        String[] parts = entryId.split("-", 2);
        long id0 = Long.parseLong(parts[0]);
        long id1 = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
        return new StreamMessageId(id0, id1);
    }

    /**
     * 向 Redis Stream 添加消息。
     *
     * <p>Redisson 实现调用 <code>stream(stream).add(StreamAddArgs.entries(fields)).toString()</code>。</p>
     *
     * @param stream Stream 名称
     * @param fields 消息字段
     * @return 实际返回<code>stream(stream).add(StreamAddArgs.entries(fields)).toString()</code>
     */
    @Override
    public String add(String stream, Map<String, String> fields) {
        return stream(stream).add(StreamAddArgs.entries(fields)).toString();
    }

    /**
     * 创建 Redis Stream 消费者组。
     *
     * <p>Redisson 实现调用 <code>stream(stream).createGroup(StreamCreateGroupArgs.name(group).id(StreamMessageId.NEWEST).makeStream())</code>。</p>
     *
     * @param stream Stream 名称
     * @param group  消费者组名
     */
    @Override
    public void createGroup(String stream, String group) {
        try {
            stream(stream).createGroup(StreamCreateGroupArgs.name(group)
                    .id(StreamMessageId.NEWEST)
                    .makeStream());
        } catch (RedisException ex) {
            if (Objects.isNull(ex.getMessage()) || !ex.getMessage().contains("BUSYGROUP")) {
                throw ex;
            }
        }
    }

    /**
     * 以指定消费者组和消费者身份读取 Redis Stream 消息。
     *
     * <p>Redisson 实现调用 <code>stream(stream)</code>。</p>
     *
     * @param group       消费者组名
     * @param consumer    消费者名称
     * @param count       每次读取的最大条数
     * @param blockMillis 阻塞等待毫秒数
     * @param streams     要读取的 Stream 集合
     * @return 实际返回<code>result</code>
     */
    @Override
    public Map<String, List<RedisStreamRecord>> readGroup(
            String group,
            String consumer,
            int count,
            int blockMillis,
            Set<String> streams) {
        Map<String, List<RedisStreamRecord>> result = new HashMap<>();
        for (String stream : streams) {
            Map<StreamMessageId, Map<String, String>> records = stream(stream).readGroup(
                    group,
                    consumer,
                    StreamReadGroupArgs.neverDelivered()
                            .count(count)
                            .timeout(Duration.ofMillis(blockMillis)));
            if (Objects.isNull(records) || records.isEmpty()) {
                continue;
            }
            List<RedisStreamRecord> normalized = new ArrayList<>();
            records.forEach((id, fields) -> normalized.add(new RedisStreamRecord(stream, id.toString(), fields, id)));
            result.put(stream, normalized);
        }
        return result;
    }

    /**
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>Redisson 实现调用 <code>stream(stream).ack(group, messageId(entryId))</code>。</p>
     *
     * @param stream  Stream 名称
     * @param group   消费者组名
     * @param entryId 消息条目 ID
     */
    @Override
    public void ack(String stream, String group, String entryId) {
        stream(stream).ack(group, messageId(entryId));
    }

    /**
     * 返回此适配器持有的原生客户端。
     *
     * @return 底层客户端实例
     */
    @Override
    public Object nativeClient() {
        return redisson;
    }

    /**
     * 仅在拥有客户端时关闭 Redisson。
     */
    @Override
    public void close() {
        if (closeClient) {
            redisson.shutdown();
        }
    }

    /**
     * 取得当前 Redis Stream 对应的 Redisson 句柄。
     *
     * @param stream 要访问的 Redis Stream 键
     * @return 给定 Redis Stream 的原生操作对象
     */
    private RStream<String, String> stream(String stream) {
        return redisson.getStream(stream);
    }
}
