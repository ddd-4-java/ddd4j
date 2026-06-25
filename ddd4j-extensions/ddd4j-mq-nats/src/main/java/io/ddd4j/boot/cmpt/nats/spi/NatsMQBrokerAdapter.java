package io.ddd4j.boot.cmpt.nats.spi;

import io.ddd4j.boot.cmpt.nats.acknowledgment.NatsMessageAcknowledgment;
import io.ddd4j.boot.cmpt.nats.acknowledgment.NatsMessageAcknowledgmentFactory;
import io.ddd4j.boot.cmpt.nats.consumer.NatsMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.nats.publisher.NatsMQEventPublisher;
import io.ddd4j.boot.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.consume.MQConsumerHandler;
import io.ddd4j.boot.mq.contract.MQMessage;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.registry.MQBrokerType;
import io.ddd4j.boot.mq.registry.MQListenerDefinition;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import io.nats.client.Connection;
import io.nats.client.Message;
import lombok.RequiredArgsConstructor;

/**
 * NATS Broker 适配器，桥接 ddd4j MQ SPI 与 jnats 客户端。
 */
@RequiredArgsConstructor
public class NatsMQBrokerAdapter implements MQBrokerAdapter {

    private final Connection connection;
    private final Ddd4jMQProperties properties;
    private final NatsMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.NATS;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new NatsMQEventPublisher(connection, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 逻辑块：优先从 NATS 原生 Message 解析 JetStream 确认
        Message natsMessage = message.nativeMessage(Message.class);
        if (natsMessage != null) {
            return NatsMessageAcknowledgmentFactory.fromNatsMessage(natsMessage)
                    .map(ack -> (MessageAcknowledgment) ack)
                    .orElse(null);
        }
        NatsMessageAcknowledgment natsAck = message.nativeMessage(NatsMessageAcknowledgment.class);
        return natsAck;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.NATS == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
