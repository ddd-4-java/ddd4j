package io.ddd4j.mq.redisstream;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;

import java.util.Objects;

/**
 * Redis Stream broker adapter for ddd4j MQ SPI.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedisStreamMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final RedisStreamMQProperties redisProperties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final UnifiedJedis jedis;
    private final RedisStreamConsumerEndpointRegistrar consumerRegistrar;

    public RedisStreamMQBrokerAdapter(RedisStreamMQProperties redisProperties, Ddd4jMQProperties mqProperties) {
        this(redisProperties, mqProperties, new JsonMQMessageSerialization(), redisProperties.newJedis());
    }

    public RedisStreamMQBrokerAdapter(
            RedisStreamMQProperties redisProperties,
            Ddd4jMQProperties mqProperties,
            MQEventSerialization serialization,
            UnifiedJedis jedis) {
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.jedis = Objects.requireNonNull(jedis, "jedis");
        this.consumerRegistrar = new RedisStreamConsumerEndpointRegistrar(jedis, redisProperties);
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.REDIS_STREAM;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new RedisStreamMQEventPublisher(jedis, props == null ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        if (message == null) {
            return null;
        }
        Object stream = message.header(RedisStreamMessageAcknowledgment.HEADER_REDIS_STREAM);
        Object group = message.header(RedisStreamMessageAcknowledgment.HEADER_REDIS_GROUP);
        Object entryId = message.header(RedisStreamMessageAcknowledgment.HEADER_REDIS_ENTRY_ID);
        if (stream instanceof String s && group instanceof String g && entryId instanceof StreamEntryID id) {
            return new RedisStreamMessageAcknowledgment(jedis, s, g, id, message.getMessageId(), message.getCorrelationId());
        }
        return null;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.REDIS_STREAM == configured;
    }

    @Override
    public void close() {
        consumerRegistrar.close();
        jedis.close();
    }
}
