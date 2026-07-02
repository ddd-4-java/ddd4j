package io.ddd4j.mq.disruptor.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.disruptor.ack.DisruptorMessageAcknowledgment;
import io.ddd4j.mq.disruptor.ack.DisruptorMessageAcknowledgmentFactory;
import io.ddd4j.mq.disruptor.consumer.DisruptorMQConsumerEndpointRegistrar;
import io.ddd4j.mq.disruptor.core.DisruptorMQBus;
import io.ddd4j.mq.disruptor.publisher.DisruptorMQEventPublisher;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * LMAX Disruptor 本地 MQ Broker 适配器。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
        if (Objects.nonNull(ack)) {
            return ack;
        }
        return DisruptorMessageAcknowledgmentFactory.from(message).orElse(null);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.DISRUPTOR == configured;
    }
}
