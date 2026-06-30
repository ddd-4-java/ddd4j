package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.registry.MQBrokerType;
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
public class RedisStreamMessageAcknowledgment implements MessageAcknowledgment {

    public static final String HEADER_REDIS_STREAM = "ddd4j.redis.stream";
    public static final String HEADER_REDIS_GROUP = "ddd4j.redis.group";
    public static final String HEADER_REDIS_ENTRY_ID = "ddd4j.redis.entryId";

    private final UnifiedJedis jedis;
    private final String stream;
    private final String group;
    private final StreamEntryID entryId;
    private final String messageId;
    private final String correlationId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    public RedisStreamMessageAcknowledgment(
            UnifiedJedis jedis,
            String stream,
            String group,
            StreamEntryID entryId,
            String messageId,
            String correlationId) {
        this.jedis = Objects.requireNonNull(jedis, "jedis");
        this.stream = Objects.requireNonNull(stream, "stream");
        this.group = Objects.requireNonNull(group, "group");
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.messageId = messageId == null ? entryId.toString() : messageId;
        this.correlationId = correlationId;
    }

    @Override
    public long deliveryTag() {
        return entryId.getTime();
    }

    @Override
    public String messageId() {
        return messageId;
    }

    @Override
    public String correlationId() {
        return correlationId;
    }

    @Override
    public boolean isOpen() {
        return true;
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
        if (acknowledged.compareAndSet(false, true)) {
            jedis.xack(stream, group, entryId);
        }
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (!requeue) {
            ack(multiple);
        }
    }

    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void recover(boolean requeue) {
        if (!requeue) {
            ack(false);
        }
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType == null) {
            return Optional.empty();
        }
        if (nativeType.isInstance(entryId)) {
            return Optional.of(nativeType.cast(entryId));
        }
        if (nativeType.isInstance(jedis)) {
            return Optional.of(nativeType.cast(jedis));
        }
        return Optional.empty();
    }
}
