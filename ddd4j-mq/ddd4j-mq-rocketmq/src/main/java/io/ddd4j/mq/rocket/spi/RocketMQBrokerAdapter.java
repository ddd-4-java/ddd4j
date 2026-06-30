package io.ddd4j.mq.rocket.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.rocket.ack.RocketMessageAcknowledgment;
import io.ddd4j.mq.rocket.ack.RocketMessageAcknowledgmentFactory;
import io.ddd4j.mq.rocket.consumer.RocketMQConsumerEndpointRegistrar;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;

/**
 * RocketMQ Broker 适配器（纯 Java，零 Spring 依赖）。
 * <p>Publisher 由 ddd4j-boot-mq-rocketmq 的 AutoConfiguration 直接创建 Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class RocketMQBrokerAdapter implements MQBrokerAdapter {

    private final Ddd4jMQProperties properties;
    private final RocketMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ROCKET;
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        RocketMessageAcknowledgment rocketAck = message.nativeMessage(RocketMessageAcknowledgment.class);
        if (rocketAck != null) {
            return rocketAck;
        }
        return RocketMessageAcknowledgmentFactory.from(message).orElse(null);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.ROCKET == configured;
    }

    public Ddd4jMQProperties properties() {
        return properties;
    }
}
