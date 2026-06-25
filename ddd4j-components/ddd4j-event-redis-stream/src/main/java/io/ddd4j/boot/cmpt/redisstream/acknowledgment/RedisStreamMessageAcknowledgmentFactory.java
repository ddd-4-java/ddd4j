package io.ddd4j.boot.cmpt.redisstream.acknowledgment;

import io.ddd4j.boot.mq.contract.MQMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 Spring Redis Stream {@link Message} 构建 {@link RedisStreamMessageAcknowledgment}。
 */
public final class RedisStreamMessageAcknowledgmentFactory {

    private RedisStreamMessageAcknowledgmentFactory() {
    }

    /**
     * 根据 Spring Message headers 解析确认对象。
     *
     * @param message          Spring 消息
     * @param stringRedisTemplate Redis 模板
     * @return 确认对象；缺少必要头时返回 empty
     */
    public static Optional<RedisStreamMessageAcknowledgment> fromSpringMessage(
            Message<?> message,
            StringRedisTemplate stringRedisTemplate) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        MessageHeaders headers = message.getHeaders();

        // 逻辑块：从 Redis Stream 头提取 streamKey / group / recordId
        String streamKey = headerAsString(headers, RedisStreamMessageAcknowledgment.HEADER_STREAM_KEY);
        String consumerGroup = headerAsString(headers, RedisStreamMessageAcknowledgment.HEADER_CONSUMER_GROUP);
        RecordId recordId = headers.get(RedisStreamMessageAcknowledgment.HEADER_RECORD_ID, RecordId.class);
        if (!hasText(streamKey) || !hasText(consumerGroup) || recordId == null) {
            return Optional.empty();
        }
        return Optional.of(new RedisStreamMessageAcknowledgment(
                stringRedisTemplate, streamKey, consumerGroup, recordId));
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message           MQ 信封
     * @param stringRedisTemplate Redis 模板
     * @return 确认对象
     */
    public static Optional<RedisStreamMessageAcknowledgment> from(
            MQMessage<?> message,
            StringRedisTemplate stringRedisTemplate) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        Object streamKeyHeader = message.headers().get(RedisStreamMessageAcknowledgment.HEADER_STREAM_KEY);
        Object groupHeader = message.headers().get(RedisStreamMessageAcknowledgment.HEADER_CONSUMER_GROUP);
        Object recordIdHeader = message.headers().get(RedisStreamMessageAcknowledgment.HEADER_RECORD_ID);
        if (!(streamKeyHeader instanceof String streamKey)
                || !(groupHeader instanceof String consumerGroup)
                || !(recordIdHeader instanceof RecordId recordId)) {
            return Optional.empty();
        }
        return Optional.of(new RedisStreamMessageAcknowledgment(
                stringRedisTemplate, streamKey, consumerGroup, recordId));
    }

    /**
     * 读取字符串类型的 header。
     */
    private static String headerAsString(MessageHeaders headers, String key) {
        Object value = headers.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 判断字符串是否有内容。
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
