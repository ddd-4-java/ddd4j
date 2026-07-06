package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.redisstream.jedis.JedisRedisStreamOperations;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;

import java.util.Objects;

/**
 * Redis Stream broker adapter for ddd4j MQ SPI.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedisStreamBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final RedisStreamMQProperties redisProperties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;
    private final RedisStreamOperations operations;
    private final RedisStreamConsumerEndpointRegistrar consumerRegistrar;

    public RedisStreamBrokerAdapter(RedisStreamMQProperties redisProperties, MQProperties mqProperties) {
        this(redisProperties, mqProperties, new JsonSerialization(), redisProperties.newOperations());
    }

    public RedisStreamBrokerAdapter(
            RedisStreamMQProperties redisProperties,
            MQProperties mqProperties,
            EventSerialization serialization,
            UnifiedJedis jedis) {
        this(redisProperties, mqProperties, serialization, new JedisRedisStreamOperations(jedis));
    }

    public RedisStreamBrokerAdapter(
            RedisStreamMQProperties redisProperties,
            MQProperties mqProperties,
            EventSerialization serialization,
            RedisStreamOperations operations) {
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.consumerRegistrar = new RedisStreamConsumerEndpointRegistrar(operations, redisProperties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.REDIS_STREAM;
    }

    @Override
    public MQEventPublisher createPublisher(MQProperties props) {
        return new RedisStreamMQEventPublisher(operations, Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        Object stream = message.header(RedisStreamAcknowledgment.HEADER_REDIS_STREAM);
        Object group = message.header(RedisStreamAcknowledgment.HEADER_REDIS_GROUP);
        Object entryId = message.header(RedisStreamAcknowledgment.HEADER_REDIS_ENTRY_ID);
        if (stream instanceof String s && group instanceof String g) {
            if (entryId instanceof StreamEntryID id) {
                return new RedisStreamAcknowledgment(operations, s, g, id.toString(), id, message.getMessageId(), message.getCorrelationId());
            }
            if (entryId instanceof String id) {
                return new RedisStreamAcknowledgment(operations, s, g, id, id, message.getMessageId(), message.getCorrelationId());
            }
        }
        return null;
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.REDIS_STREAM == configured;
    }

    @Override
    public void close() {
        consumerRegistrar.close();
        operations.close();
    }
}
