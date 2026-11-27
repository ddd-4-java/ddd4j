package io.ddd4j.mq.redisstream;

/**
 * Redis Stream 适配器使用的 Redis 客户端后端类型枚举。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum RedisStreamClientType {

    /** Jedis \u5ba2\u6237\u7aef\u3002 */
    JEDIS,
    /** Redisson \u5ba2\u6237\u7aef\u3002 */
    REDISSON,
    /** Lettuce \u5ba2\u6237\u7aef\u3002 */
    LETTUCE
}
