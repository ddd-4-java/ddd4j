package io.ddd4j.mq.redisstream.spi;

import io.ddd4j.mq.redisstream.ack.RedisStreamMessageAcknowledgment;
import io.ddd4j.mq.redisstream.ack.RedisStreamMessageAcknowledgmentFactory;
import io.ddd4j.mq.redisstream.consumer.RedisStreamConsumerEndpointRegistrar;
import io.ddd4j.mq.redisstream.publisher.RedisStreamMQEventPublisher;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Stream Broker 适配器，桥接 ddd4j MQ SPI 与 Spring Data Redis Stream。
 * <p>2.0.x 重构：基于纯 Java {@link MQMessage}，不再依赖 {@code org.springframework.messaging.Message}。
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
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new RedisStreamMQEventPublisher(stringRedisTemplate, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 2.0.x：直接基于纯 Java MQMessage 解析（Redis Stream 原生 Record 通过 MQMessage 头传入）
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

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
