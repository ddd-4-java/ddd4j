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

    /** \u6267\u884c Redis Stream \u64cd\u4f5c\u7684 Redisson \u5ba2\u6237\u7aef\u3002 */
    private final RedissonClient redisson;
    /** \u5173\u95ed\u9002\u914d\u5668\u65f6\u662f\u5426\u5e94\u5173\u95ed\u5f53\u524d Redisson \u5ba2\u6237\u7aef\u3002 */
    private final boolean closeClient;

    /**
     * \u4ece Redis \u5730\u5740\u521b\u5efa Redisson Stream \u64cd\u4f5c\u9002\u914d\u5668\u3002
     * @param redisUrl Redis \u8fde\u63a5\u5730\u5740
     */
    public RedissonRedisStreamOperations(String redisUrl) {
        this(createClient(redisUrl), true);
    }

    /**
     * \u590d\u7528\u5df2\u6709 Redisson \u5ba2\u6237\u7aef\u3002
     * @param redisson Redisson \u5ba2\u6237\u7aef
     */
    public RedissonRedisStreamOperations(RedissonClient redisson) {
        this(redisson, false);
    }

    /**
     * \u6307\u5b9a\u5ba2\u6237\u7aef\u751f\u547d\u5468\u671f\u5f52\u5c5e\u540e\u521b\u5efa\u64cd\u4f5c\u9002\u914d\u5668\u3002
     * @param redisson Redisson \u5ba2\u6237\u7aef
     * @param closeClient \u5173\u95ed\u9002\u914d\u5668\u65f6\u662f\u5426\u5173\u95ed\u5ba2\u6237\u7aef
     */
    public RedissonRedisStreamOperations(RedissonClient redisson, boolean closeClient) {
        this.redisson = Objects.requireNonNull(redisson, "redisson");
        this.closeClient = closeClient;
    }

    /**
     * \u6309\u7ed9\u5b9a\u5730\u5740\u521b\u5efa\u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684 Redis \u5ba2\u6237\u7aef\u3002
     * @param redisUrl Redis \u8fde\u63a5\u5730\u5740
     * @return \u65b0\u5efa\u7684\u539f\u751f Redis \u5ba2\u6237\u7aef
     */
    private static RedissonClient createClient(String redisUrl) {
        Config config = new Config();
        config.useSingleServer().setAddress(redisUrl);
        return Redisson.create(config);
    }

    /**
     * \u5c06\u65f6\u95f4\u90e8\u5206\u548c\u5e8f\u53f7\u90e8\u5206\u7ec4\u6210\u7684\u6587\u672c\u8f6c\u6362\u4e3a Redisson Stream \u6807\u8bc6\u3002
     * @param entryId Redis Stream \u6761\u76ee\u7684\u6587\u672c\u6807\u8bc6
     * @return \u5bf9\u5e94\u7684 Redisson \u6d88\u606f\u6807\u8bc6
     */
    private static StreamMessageId messageId(String entryId) {
        String[] parts = entryId.split("-", 2);
        long id0 = Long.parseLong(parts[0]);
        long id1 = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
        return new StreamMessageId(id0, id1);
    }

    /**
     * add() 方法。
     * @param stream 字符串参数。
     * @param fields 字段列表。
     * @return 结果字符串。
     */
    @Override
    public String add(String stream, Map<String, String> fields) {
        return stream(stream).add(StreamAddArgs.entries(fields)).toString();
    }

    /**
     * 创建对象。
     * @param stream 字符串参数。
     * @param group 消费组。
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
     * @param group 消费者组名
     * @param consumer 消费者名称
     * @param count 每次读取的最大条数
     * @param blockMillis 阻塞等待毫秒数
     * @param streams 要读取的 Stream 集合
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
     * ack() 方法。
     * @param stream 字符串参数。
     * @param group 消费组。
     * @param entryId 字符串参数。
     */
    @Override
    public void ack(String stream, String group, String entryId) {
        stream(stream).ack(group, messageId(entryId));
    }

    /**
     * nativeClient() 方法。
     * @return 结果对象。
     */
    @Override
    public Object nativeClient() {
        return redisson;
    }

    /**
     * 关闭并释放资源。
     */
    @Override
    public void close() {
        if (closeClient) {
            redisson.shutdown();
        }
    }

    /**
     * \u53d6\u5f97\u5f53\u524d Redis Stream \u5bf9\u5e94\u7684 Redisson \u53e5\u67c4\u3002
     * @param stream \u8981\u8bbf\u95ee\u7684 Redis Stream \u952e
     * @return \u7ed9\u5b9a Redis Stream \u7684\u539f\u751f\u64cd\u4f5c\u5bf9\u8c61
     */
    private RStream<String, String> stream(String stream) {
        return redisson.getStream(stream);
    }
}
