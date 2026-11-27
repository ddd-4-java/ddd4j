package io.ddd4j.mq.redisstream.jedis;

import io.ddd4j.mq.redisstream.RedisStreamOperations;
import io.ddd4j.mq.redisstream.RedisStreamRecord;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.*;

/**
 * 基于 Jedis 客户端的 Redis Stream 操作实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class JedisRedisStreamOperations implements RedisStreamOperations {

    /** \u6267\u884c Redis Stream \u64cd\u4f5c\u7684 Jedis \u5ba2\u6237\u7aef\u3002 */
    private final UnifiedJedis jedis;

    /**
     * \u521b\u5efa Jedis Stream \u64cd\u4f5c\u9002\u914d\u5668\u3002
     * @param jedis Jedis \u5ba2\u6237\u7aef
     */
    public JedisRedisStreamOperations(UnifiedJedis jedis) {
        this.jedis = Objects.requireNonNull(jedis, "jedis");
    }

    /**
     * 向 Redis Stream 添加消息。
     *
     * <p>Jedis 实现调用 <code>jedis.xadd(stream, XAddParams.xAddParams().id(StreamEntryID.NEW_ENTRY), fields).toString()</code>。</p>
     *
     * @param stream Stream 名称
     * @param fields 消息字段
     * @return 实际返回<code>jedis.xadd(stream, XAddParams.xAddParams().id(StreamEntryID.NEW_ENTRY), fields).toString()</code>
     */
    @Override
    public String add(String stream, Map<String, String> fields) {
        return jedis.xadd(stream, XAddParams.xAddParams().id(StreamEntryID.NEW_ENTRY), fields).toString();
    }

    /**
     * 创建 Redis Stream 消费者组。
     *
     * <p>Jedis 实现调用 <code>jedis.xgroupCreate(stream, group, StreamEntryID.XGROUP_LAST_ENTRY, true)</code>。</p>
     *
     * @param stream Stream 名称
     * @param group 消费者组名
     */
    @Override
    public void createGroup(String stream, String group) {
        try {
            jedis.xgroupCreate(stream, group, StreamEntryID.XGROUP_LAST_ENTRY, true);
        } catch (JedisDataException ex) {
            if (Objects.isNull(ex.getMessage()) || !ex.getMessage().contains("BUSYGROUP")) {
                throw ex;
            }
        }
    }

    /**
     * 以指定消费者组和消费者身份读取 Redis Stream 消息。
     *
     * <p>Jedis 实现调用 <code>XReadGroupParams.xReadGroupParams().count(count).block(blockMillis)</code>。</p>
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
        XReadGroupParams params = XReadGroupParams.xReadGroupParams()
                .count(count)
                .block(blockMillis);
        Map<String, StreamEntryID> offsets = new HashMap<>();
        streams.forEach(stream -> offsets.put(stream, StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY));
        Map<String, List<StreamEntry>> records = jedis.xreadGroupAsMap(group, consumer, params, offsets);
        Map<String, List<RedisStreamRecord>> result = new HashMap<>();
        if (Objects.isNull(records) || records.isEmpty()) {
            return result;
        }
        records.forEach((stream, entries) -> {
            List<RedisStreamRecord> normalized = new ArrayList<>();
            for (StreamEntry entry : entries) {
                normalized.add(new RedisStreamRecord(stream, entry.getID().toString(), entry.getFields(), entry));
            }
            result.put(stream, normalized);
        });
        return result;
    }

    /**
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>Jedis 实现调用 <code>jedis.xack(stream, group, new StreamEntryID(entryId))</code>。</p>
     *
     * @param stream Stream 名称
     * @param group 消费者组名
     * @param entryId 消息条目 ID
     */
    @Override
    public void ack(String stream, String group, String entryId) {
        jedis.xack(stream, group, new StreamEntryID(entryId));
    }

    /**
     * 返回此适配器持有的原生客户端。
     *
     * @return 底层客户端实例
     */
    @Override
    public Object nativeClient() {
        return jedis;
    }

    /** \u5173\u95ed\u5f53\u524d\u64cd\u4f5c\u5bf9\u8c61\u4f7f\u7528\u7684 Jedis \u5ba2\u6237\u7aef\u3002 */
    @Override
    public void close() {
        jedis.close();
    }
}
