package io.ddd4j.mq.redisstream;

import java.util.Map;

/**
 * Normalized Redis Stream record across Jedis, Redisson and Lettuce.
 */
public record RedisStreamRecord(String stream, String id, Map<String, String> fields, Object nativeMessage) {
}
