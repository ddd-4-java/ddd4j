package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.redisstream.jedis.JedisRedisStreamOperations;
import io.ddd4j.mq.redisstream.lettuce.LettuceRedisStreamOperations;
import io.ddd4j.mq.redisstream.redisson.RedissonRedisStreamOperations;
import lombok.Data;
import lombok.EqualsAndHashCode;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.UnifiedJedis;

import java.util.Objects;

/**
 * Redis Stream adapter configuration.
 *
 * <p>{@link RedisStreamMQProperties} extends {@link MQProperties} —— 复用通用字段（namespace / defaultTopic /
 * autoAck / persist / retries / username / password / database 等），仅声明 Redis Stream 专属字段。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RedisStreamMQProperties extends MQProperties {

    /**
     * Redis 连接 URL（例：{@code redis://localhost:6379} 或 {@code redis://:pwd@host:6379/0}）。
     */
    private String url = "redis://localhost:6379";
    /**
     * 消费者名称
     */
    private String consumerName = "ddd4j";
    /**
     * 单次 XREADGROUP 拉取条数
     */
    private int count = 10;
    /**
     * XREADGROUP 阻塞毫秒数
     */
    private int blockMillis = 1000;
    /**
     * 是否自动创建消费组
     */
    private boolean autoCreateGroup = true;
    /**
     * 是否自动启动消费者
     */
    private boolean autoStartConsumers = true;
    /**
     * Redis 客户端后端类型
     */
    private RedisStreamClientType clientType = RedisStreamClientType.JEDIS;

    /**
     * \u57fa\u4e8e {@link #url} \u521b\u5efa Jedis\uff08UnifiedJedis \u5f62\u6001\uff09\u3002
     *
     * @return Jedis \u5ba2\u6237\u7aef
     */
    public UnifiedJedis newJedis() {
        return RedisClient.create(url);
    }

    /**
     * \u57fa\u4e8e {@link #clientType} \u521b\u5efa\u5bf9\u5e94\u540e\u7aef\u7684 Stream \u64cd\u4f5c\u9002\u914d\u3002
     *
     * @return Stream \u64cd\u4f5c\u9002\u914d\u5668
     */
    public RedisStreamOperations newOperations() {
        return switch (clientType) {
            case JEDIS -> new JedisRedisStreamOperations(newJedis());
            case REDISSON -> new RedissonRedisStreamOperations(url);
            case LETTUCE -> new LettuceRedisStreamOperations(url);
        };
    }

    /**
     * \u8bbe\u7f6e\u5ba2\u6237\u7aef\u7c7b\u578b\uff08null \u515c\u5e95\u4e3a JEDIS\uff09\u3002
     *
     * @param clientType \u5ba2\u6237\u7aef\u540e\u7aef\u7c7b\u578b
     */
    public void setClientType(RedisStreamClientType clientType) {
        this.clientType = Objects.isNull(clientType) ? RedisStreamClientType.JEDIS : clientType;
    }
}
