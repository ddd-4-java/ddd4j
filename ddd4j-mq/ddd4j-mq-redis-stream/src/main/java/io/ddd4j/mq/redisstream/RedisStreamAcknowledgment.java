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

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.redisstream.jedis.JedisRedisStreamOperations;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Stream manual acknowledgment mapping.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedisStreamAcknowledgment implements Acknowledgment {

    /** Redis Stream \u540d\u79f0\u8bf7\u6c42\u5934\u3002 */
    public static final String HEADER_REDIS_STREAM = "ddd4j.redis.stream";
    /** Redis \u6d88\u8d39\u8005\u7ec4\u8bf7\u6c42\u5934\u3002 */
    public static final String HEADER_REDIS_GROUP = "ddd4j.redis.group";
    /** Redis Stream \u6761\u76ee\u6807\u8bc6\u8bf7\u6c42\u5934\u3002 */
    public static final String HEADER_REDIS_ENTRY_ID = "ddd4j.redis.entryId";

    /** \u5b8c\u6210 Redis Stream \u6d88\u8d39\u786e\u8ba4\u7684\u5ba2\u6237\u7aef\u64cd\u4f5c\u5165\u53e3\u3002 */
    private final RedisStreamOperations operations;
    /** \u5f53\u524d\u6d88\u606f\u6240\u5c5e\u7684 Redis Stream \u952e\u3002 */
    private final String stream;
    /** \u5f53\u524d\u6d88\u606f\u6240\u5904\u7684 Redis Stream \u6d88\u8d39\u8005\u7ec4\u3002 */
    private final String group;
    /** \u4f5c\u4e3a\u6587\u672c\u4f20\u9012\u7684 Redis Stream \u8bb0\u5f55\u6807\u8bc6\u3002 */
    private final String entryId;
    /** \u539f\u751f Redis \u5ba2\u6237\u7aef\u8fd4\u56de\u7684 Stream \u8bb0\u5f55\u6807\u8bc6\u3002 */
    private final Object nativeEntryId;
    /** \u6d88\u606f\u6807\u8bc6\u3002 */
    private final String messageId;
    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * \u4f7f\u7528 Jedis \u521b\u5efa Redis Stream \u6d88\u606f\u786e\u8ba4\u53e5\u67c4\u3002
     * @param jedis Jedis \u5ba2\u6237\u7aef
     * @param stream Stream \u540d\u79f0
     * @param group \u6d88\u8d39\u8005\u7ec4
     * @param entryId \u6761\u76ee\u6807\u8bc6
     * @param messageId \u6d88\u606f\u6807\u8bc6
     * @param correlationId \u5173\u8054\u6807\u8bc6
     */
    public RedisStreamAcknowledgment(
            UnifiedJedis jedis,
            String stream,
            String group,
            StreamEntryID entryId,
            String messageId,
            String correlationId) {
        this(new JedisRedisStreamOperations(jedis), stream, group, entryId.toString(), entryId, messageId, correlationId);
    }

    /**
     * \u4f7f\u7528 Stream \u64cd\u4f5c\u9002\u914d\u5668\u521b\u5efa\u6d88\u606f\u786e\u8ba4\u53e5\u67c4\u3002
     * @param operations Stream \u64cd\u4f5c\u9002\u914d\u5668
     * @param stream Stream \u540d\u79f0
     * @param group \u6d88\u8d39\u8005\u7ec4
     * @param entryId \u6761\u76ee\u6807\u8bc6
     * @param nativeEntryId \u5e95\u5c42\u5ba2\u6237\u7aef\u6761\u76ee\u6807\u8bc6
     * @param messageId \u6d88\u606f\u6807\u8bc6
     * @param correlationId \u5173\u8054\u6807\u8bc6
     */
    public RedisStreamAcknowledgment(
            RedisStreamOperations operations,
            String stream,
            String group,
            String entryId,
            Object nativeEntryId,
            String messageId,
            String correlationId) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.stream = Objects.requireNonNull(stream, "stream");
        this.group = Objects.requireNonNull(group, "group");
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.nativeEntryId = nativeEntryId;
        this.messageId = Objects.isNull(messageId) ? entryId : messageId;
        this.correlationId = correlationId;
    }

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return RedisStreamIds.deliveryTag(entryId);
    }

    /** {@inheritDoc} */
    @Override
    public String messageId() {
        return messageId;
    }

    /** {@inheritDoc} */
    @Override
    public String correlationId() {
        return correlationId;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOpen() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public BrokerType brokerType() {
        return BrokerType.REDIS_STREAM;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void ack(boolean multiple) {
        if (!acknowledged.get()) {
            operations.ack(stream, group, entryId);
            acknowledged.set(true);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void nack(boolean multiple, boolean requeue) {
        if (requeue) {
            // Redis Stream 通过保留 PEL 记录表达重投；标记已处理以阻止调用方随后自动 XACK。
            acknowledged.compareAndSet(false, true);
        } else {
            ack(multiple);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void recover(boolean requeue) {
        if (!requeue) {
            ack(false);
        }
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (Objects.isNull(nativeType)) {
            return Optional.empty();
        }
        if (nativeType.isInstance(entryId)) {
            return Optional.of(nativeType.cast(entryId));
        }
        if (Objects.nonNull(nativeEntryId) && nativeType.isInstance(nativeEntryId)) {
            return Optional.of(nativeType.cast(nativeEntryId));
        }
        Object nativeClient = operations.nativeClient();
        if (Objects.nonNull(nativeClient) && nativeType.isInstance(nativeClient)) {
            return Optional.of(nativeType.cast(nativeClient));
        }
        return Optional.empty();
    }
}
