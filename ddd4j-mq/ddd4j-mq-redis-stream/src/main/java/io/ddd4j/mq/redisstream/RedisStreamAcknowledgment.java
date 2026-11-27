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

    /**
     * 返回本次消息投递的标识。
     *
     * <p>此实现调用 <code>RedisStreamIds.deliveryTag(entryId)</code>。</p>
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return RedisStreamIds.deliveryTag(entryId);
    }

    /**
     * 返回收到的消息标识。
     *
     * @return 当前消息的标识
     */
    @Override
    public String messageId() {
        return messageId;
    }

    /**
     * 返回消息所属链路的关联标识。
     *
     * @return 当前消息关联的请求或事件标识
     */
    @Override
    public String correlationId() {
        return correlationId;
    }

    /**
     * 检查当前投递的确认对象是否仍可用。
     *
     * @return 此实现返回<code>true</code>
     */
    @Override
    public boolean isOpen() {
        return true;
    }

    /**
     * 判断当前投递是否已完成确认处理。
     *
     * <p>此实现调用 <code>acknowledged.get()</code>。</p>
     *
     * @return 此投递已经进入确认终态时为真
     */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /**
     * 返回当前投递所使用的消息中间件类型。
     *
     * @return 客户端或投递协议对应的 broker 枚举值
     */
    @Override
    public BrokerType brokerType() {
        return BrokerType.REDIS_STREAM;
    }

    /**
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>此实现调用 <code>ack(false)</code>。</p>
     */
    @Override
    public void ack() {
        ack(false);
    }

    /**
     * 通过底层消息会话更新当前投递的确认状态。
     *
     * @param multiple 是否批量确认至当前 tag
     */
    @Override
    public synchronized void ack(boolean multiple) {
        if (!acknowledged.get()) {
            operations.ack(stream, group, entryId);
            acknowledged.set(true);
        }
    }

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>此实现调用 <code>nack(false, requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * 通过底层消息会话更新当前投递的确认状态。
     *
     * @param multiple 是否批量否定
     * @param requeue 是否重新入队
     */
    @Override
    public synchronized void nack(boolean multiple, boolean requeue) {
        if (requeue) {
            // Redis Stream 通过保留 PEL 记录表达重投；标记已处理以阻止调用方随后自动 XACK。
            acknowledged.compareAndSet(false, true);
        } else {
            ack(multiple);
        }
    }

    /**
     * 拒绝当前投递，并按约定处理是否重新入队。
     *
     * <p>此实现调用 <code>nack(false, requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * 请求底层消息会话恢复未处理的投递。
     *
     * <p>此实现调用 <code>ack(false)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        if (!requeue) {
            ack(false);
        }
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * <p>此实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param <T> 类型参数
     * @param nativeType 原生类型
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(nativeType.cast(entryId))</code>、<code>Optional.of(nativeType.cast(nativeEntryId))</code>
     */
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
