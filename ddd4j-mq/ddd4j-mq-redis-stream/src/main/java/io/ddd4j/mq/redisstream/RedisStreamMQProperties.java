package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.redisstream.jedis.JedisRedisStreamOperations;
import io.ddd4j.mq.redisstream.lettuce.LettuceRedisStreamOperations;
import io.ddd4j.mq.redisstream.redisson.RedissonRedisStreamOperations;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.UnifiedJedis;

/**
 * Redis Stream adapter configuration.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedisStreamMQProperties {

    private String url = "redis://localhost:6379";
    private String consumerName = "ddd4j";
    private int count = 10;
    private int blockMillis = 1000;
    private boolean autoCreateGroup = true;
    private boolean autoStartConsumers = true;
    private RedisStreamClientType clientType = RedisStreamClientType.JEDIS;

    public UnifiedJedis newJedis() {
        return RedisClient.create(url);
    }

    public RedisStreamOperations newOperations() {
        return switch (clientType) {
            case JEDIS -> new JedisRedisStreamOperations(newJedis());
            case REDISSON -> new RedissonRedisStreamOperations(url);
            case LETTUCE -> new LettuceRedisStreamOperations(url);
        };
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getBlockMillis() {
        return blockMillis;
    }

    public void setBlockMillis(int blockMillis) {
        this.blockMillis = blockMillis;
    }

    public boolean isAutoCreateGroup() {
        return autoCreateGroup;
    }

    public void setAutoCreateGroup(boolean autoCreateGroup) {
        this.autoCreateGroup = autoCreateGroup;
    }

    public boolean isAutoStartConsumers() {
        return autoStartConsumers;
    }

    public void setAutoStartConsumers(boolean autoStartConsumers) {
        this.autoStartConsumers = autoStartConsumers;
    }

    public RedisStreamClientType getClientType() {
        return clientType;
    }

    public void setClientType(RedisStreamClientType clientType) {
        this.clientType = clientType == null ? RedisStreamClientType.JEDIS : clientType;
    }
}
