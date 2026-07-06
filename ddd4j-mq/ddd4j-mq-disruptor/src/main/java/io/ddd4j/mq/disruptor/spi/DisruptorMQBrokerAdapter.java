package io.ddd4j.mq.disruptor.spi;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.disruptor.ack.DisruptorAcknowledgment;
import io.ddd4j.mq.disruptor.ack.DisruptorAcknowledgmentFactory;
import io.ddd4j.mq.disruptor.consumer.DisruptorMQConsumerEndpointRegistrar;
import io.ddd4j.mq.disruptor.core.DisruptorMQBus;
import io.ddd4j.mq.disruptor.publisher.DisruptorEventPublisher;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.spi.BrokerAdapter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * LMAX Disruptor 本地 MQ Broker 适配器。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class DisruptorBrokerAdapter implements BrokerAdapter {

    private final DisruptorMQBus disruptorMQBus;
    private final MQProperties properties;
    private final DisruptorMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public BrokerType brokerType() {
        return BrokerType.DISRUPTOR;
    }

    @Override
    public EventPublisher createPublisher(MQProperties props) {
        return new DisruptorEventPublisher(disruptorMQBus, props);
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        DisruptorAcknowledgment ack = message.nativeMessage(DisruptorAcknowledgment.class);
        if (Objects.nonNull(ack)) {
            return ack;
        }
        return DisruptorAcknowledgmentFactory.from(message).orElse(null);
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.DISRUPTOR == configured;
    }
}
