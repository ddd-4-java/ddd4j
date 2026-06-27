package io.ddd4j.mq.rocket.spi;

import io.ddd4j.mq.rocket.ack.RocketMessageAcknowledgment;
import io.ddd4j.mq.rocket.ack.RocketMessageAcknowledgmentFactory;
import io.ddd4j.mq.rocket.consumer.RocketMQConsumerEndpointRegistrar;
import io.ddd4j.mq.rocket.publisher.RocketMQEventPublisher;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;

/**
 * RocketMQ Broker 适配器，桥接 ddd4j MQ SPI 与 RocketMQ Spring。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class RocketMQBrokerAdapter implements MQBrokerAdapter {

    private final RocketMQTemplate rocketMQTemplate;
    private final Ddd4jMQProperties properties;
    private final RocketMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ROCKET;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new RocketMQEventPublisher(rocketMQTemplate, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 逻辑块：优先从 Spring Message 原生对象解析 RocketMQ 确认
        Message<?> springMessage = message.nativeMessage(Message.class);
        if (springMessage != null) {
            return RocketMessageAcknowledgmentFactory.fromSpringMessage(springMessage)
                    .map(ack -> (MessageAcknowledgment) ack)
                    .orElse(null);
        }
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

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
