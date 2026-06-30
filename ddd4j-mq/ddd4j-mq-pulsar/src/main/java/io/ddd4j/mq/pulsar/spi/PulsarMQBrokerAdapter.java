package io.ddd4j.mq.pulsar.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.pulsar.ack.PulsarMessageAcknowledgment;
import io.ddd4j.mq.pulsar.ack.PulsarMessageAcknowledgmentFactory;
import io.ddd4j.mq.pulsar.consumer.PulsarConsumerEndpointRegistrar;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;

/**
 * Pulsar Broker 适配器（纯 Java，零 Spring 依赖）。
 * <p>Publisher 由 ddd4j-boot-mq-pulsar 的 AutoConfiguration 直接创建 Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class PulsarMQBrokerAdapter implements MQBrokerAdapter {

    private final Ddd4jMQProperties properties;
    private final PulsarConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.PULSAR;
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        PulsarMessageAcknowledgment pulsarAck = message.nativeMessage(PulsarMessageAcknowledgment.class);
        if (pulsarAck != null) {
            return pulsarAck;
        }
        return PulsarMessageAcknowledgmentFactory.from(message).orElse(null);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.PULSAR == configured;
    }

    public Ddd4jMQProperties properties() {
        return properties;
    }
}
