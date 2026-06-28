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

/**
 * RocketMQ Broker 适配器，桥接 ddd4j MQ SPI 与 RocketMQ Spring。
 * <p>2.0.x 重构：基于纯 Java {@link MQMessage}，不再依赖 {@code org.springframework.messaging.Message}。
 *
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
        // 2.0.x：直接基于纯 Java MQMessage 解析（MessageExt 已通过 nativeMessage 逃生口传入）
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
