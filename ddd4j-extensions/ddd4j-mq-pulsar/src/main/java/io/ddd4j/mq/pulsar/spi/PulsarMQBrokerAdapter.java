package io.ddd4j.mq.pulsar.spi;

import io.ddd4j.mq.pulsar.acknowledgment.PulsarMessageAcknowledgment;
import io.ddd4j.mq.pulsar.acknowledgment.PulsarMessageAcknowledgmentFactory;
import io.ddd4j.mq.pulsar.consumer.PulsarConsumerEndpointRegistrar;
import io.ddd4j.mq.pulsar.publisher.PulsarMQEventPublisher;
import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.apache.pulsar.client.api.PulsarClient;

/**
 * Pulsar Broker 适配器，桥接 ddd4j MQ SPI 与原生 Pulsar Java Client。
 */
@RequiredArgsConstructor
public class PulsarMQBrokerAdapter implements MQBrokerAdapter {

    private final PulsarClient pulsarClient;
    private final Ddd4jMQProperties properties;
    private final PulsarConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.PULSAR;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new PulsarMQEventPublisher(pulsarClient, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 逻辑块：优先从 Spring Message 原生对象解析 Pulsar 确认
        Message<?> springMessage = message.nativeMessage(Message.class);
        if (springMessage != null) {
            return PulsarMessageAcknowledgmentFactory.fromSpringMessage(springMessage)
                    .map(ack -> (MessageAcknowledgment) ack)
                    .orElse(null);
        }
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

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
