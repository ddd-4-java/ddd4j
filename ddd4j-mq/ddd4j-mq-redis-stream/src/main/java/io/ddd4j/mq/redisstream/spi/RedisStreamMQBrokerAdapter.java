package io.ddd4j.mq.redisstream.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.redisstream.ack.RedisStreamMessageAcknowledgment;
import io.ddd4j.mq.redisstream.ack.RedisStreamMessageAcknowledgmentFactory;
import io.ddd4j.mq.redisstream.consumer.RedisStreamConsumerEndpointRegistrar;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Stream Broker 适配器（纯 Java，零 Spring 依赖）。
 * <p>Publisher 由 ddd4j-boot-mq-redis-stream 的 AutoConfiguration 直接创建 Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class RedisStreamMQBrokerAdapter implements MQBrokerAdapter {

    private final StringRedisTemplate stringRedisTemplate;
    private final Ddd4jMQProperties properties;
    private final RedisStreamConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.REDIS_STREAM;
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        RedisStreamMessageAcknowledgment redisAck = message.nativeMessage(RedisStreamMessageAcknowledgment.class);
        if (redisAck != null) {
            return redisAck;
        }
        return RedisStreamMessageAcknowledgmentFactory.from(message, stringRedisTemplate).orElse(null);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.REDIS_STREAM == configured;
    }

    public Ddd4jMQProperties properties() {
        return properties;
    }
}
