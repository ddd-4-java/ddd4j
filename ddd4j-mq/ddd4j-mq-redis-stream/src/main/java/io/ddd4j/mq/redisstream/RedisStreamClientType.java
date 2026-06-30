package io.ddd4j.mq.redisstream;

/**
 * Redis client backend used by the Redis Stream adapter.
 */
public enum RedisStreamClientType {

    JEDIS,
    REDISSON,
    LETTUCE
}
