package io.ddd4j.boot.cmpt.disruptor.spi;

import io.ddd4j.boot.cmpt.disruptor.acknowledgment.DisruptorMessageAcknowledgment;
import io.ddd4j.boot.cmpt.disruptor.acknowledgment.DisruptorMessageAcknowledgmentFactory;
import io.ddd4j.boot.cmpt.disruptor.consumer.DisruptorMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.disruptor.core.DisruptorMQBus;
import io.ddd4j.boot.cmpt.disruptor.publisher.DisruptorMQEventPublisher;
import io.ddd4j.boot.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.consume.MQConsumerHandler;
import io.ddd4j.boot.mq.contract.MQMessage;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.registry.MQBrokerType;
import io.ddd4j.boot.mq.registry.MQListenerDefinition;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;

/**
 * LMAX Disruptor 本地 MQ Broker 适配器。
 */
@RequiredArgsConstructor
public class DisruptorMQBrokerAdapter implements MQBrokerAdapter {

    private final DisruptorMQBus disruptorMQBus;
    private final Ddd4jMQProperties properties;
    private final DisruptorMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.DISRUPTOR;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new DisruptorMQEventPublisher(disruptorMQBus, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        DisruptorMessageAcknowledgment ack = message.nativeMessage(DisruptorMessageAcknowledgment.class);
        if (ack != null) {
            return ack;
        }
        return DisruptorMessageAcknowledgmentFactory.from(message).orElse(null);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.DISRUPTOR == configured;
    }
}
