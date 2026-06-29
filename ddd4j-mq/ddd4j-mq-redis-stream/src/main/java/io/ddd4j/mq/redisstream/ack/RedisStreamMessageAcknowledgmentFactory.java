package io.ddd4j.mq.redisstream.ack;

import io.ddd4j.mq.contract.MQMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 从纯 Java {@link MQMessage} 头信息构建 {@link RedisStreamMessageAcknowledgment}。
 *
 * <p>2.0.x 重构：彻底移除对 {@code org.springframework.messaging.Message} 的依赖，
 * 直接基于 ddd4j-mq-core 的纯 Java {@link MQMessage} 工作。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class RedisStreamMessageAcknowledgmentFactory {

    private RedisStreamMessageAcknowledgmentFactory() {
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message             MQ 信封
     * @param stringRedisTemplate Redis 模板
     * @return 确认对象；缺少必要头时返回 empty
     */
    public static Optional<RedisStreamMessageAcknowledgment> from(
            MQMessage<?> message,
            StringRedisTemplate stringRedisTemplate) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        Map<String, Object> headers = message.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }

        Object streamKeyHeader = headers.get(RedisStreamMessageAcknowledgment.HEADER_STREAM_KEY);
        Object groupHeader = headers.get(RedisStreamMessageAcknowledgment.HEADER_CONSUMER_GROUP);
        Object recordIdHeader = headers.get(RedisStreamMessageAcknowledgment.HEADER_RECORD_ID);
        if (!(streamKeyHeader instanceof String streamKey)
                || !(groupHeader instanceof String consumerGroup)
                || !(recordIdHeader instanceof RecordId recordId)) {
            return Optional.empty();
        }
        return Optional.of(new RedisStreamMessageAcknowledgment(
                stringRedisTemplate, streamKey, consumerGroup, recordId));
    }
}
