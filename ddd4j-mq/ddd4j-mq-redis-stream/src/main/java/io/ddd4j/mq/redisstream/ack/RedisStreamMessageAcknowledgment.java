package io.ddd4j.mq.redisstream.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 Redis Stream XACK 的消息确认实现。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class RedisStreamMessageAcknowledgment implements MessageAcknowledgment {

    /** MQMessage headers 中存放 stream key 的键 */
    public static final String HEADER_STREAM_KEY = "redis.stream.key";

    /** MQMessage headers 中存放 consumer group 的键 */
    public static final String HEADER_CONSUMER_GROUP = "redis.stream.group";

    /** MQMessage headers 中存放 RecordId 的键 */
    public static final String HEADER_RECORD_ID = "redis.stream.recordId";

    private final StringRedisTemplate stringRedisTemplate;
    private final String streamKey;
    private final String consumerGroup;
    private final RecordId recordId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 Redis Stream 确认对象。
     *
     * @param stringRedisTemplate Redis 模板
     * @param streamKey           Stream key
     * @param consumerGroup       消费组
     * @param recordId            记录 ID
     */
    public RedisStreamMessageAcknowledgment(StringRedisTemplate stringRedisTemplate,
                                            String streamKey,
                                            String consumerGroup,
                                            RecordId recordId) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        this.streamKey = Objects.requireNonNull(streamKey, "streamKey");
        this.consumerGroup = Objects.requireNonNull(consumerGroup, "consumerGroup");
        this.recordId = Objects.requireNonNull(recordId, "recordId");
    }

    @Override
    public long deliveryTag() {
        return recordId.hashCode();
    }

    @Override
    public String messageId() {
        return recordId.getValue();
    }

    @Override
    public String correlationId() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return !acknowledged.get();
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.REDIS_STREAM;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            throw new UnsupportedAckOperationException(MQBrokerType.REDIS_STREAM, "ack(multiple=true)");
        }
        try {
            // 逻辑块：XACK 确认消费
            stringRedisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, recordId);
            acknowledged.set(true);
        } catch (Exception ex) {
            throw new IllegalStateException("Redis Stream XACK failed, recordId=" + recordId, ex);
        }
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        if (multiple) {
            throw new UnsupportedAckOperationException(MQBrokerType.REDIS_STREAM, "nack(multiple=true)");
        }
        if (!requeue) {
            ack(false);
        } else {
            // 逻辑块：不 XACK 即保留 pending，等待 claim/重投
            acknowledged.set(true);
        }
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedAckOperationException(MQBrokerType.REDIS_STREAM, "recover");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (StringRedisTemplate.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(stringRedisTemplate));
        }
        if (RecordId.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(recordId));
        }
        if (RedisStreamMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回 Stream key。
     */
    public String streamKey() {
        return streamKey;
    }

    /**
     * 返回消费组名称。
     */
    public String consumerGroup() {
        return consumerGroup;
    }

    /**
     * 返回记录 ID。
     */
    public RecordId recordId() {
        return recordId;
    }

    /**
     * 防止重复确认。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new UnsupportedAckOperationException("Message already acknowledged, recordId=" + recordId);
        }
    }
}
