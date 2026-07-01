package io.ddd4j.mq.nats.spi;

import java.util.Objects;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.nats.ack.NatsMessageAcknowledgment;
import io.ddd4j.mq.nats.ack.NatsMessageAcknowledgmentFactory;
import io.ddd4j.mq.nats.consumer.NatsMQConsumerEndpointRegistrar;
import io.ddd4j.mq.nats.publisher.NatsMQEventPublisher;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import io.nats.client.Connection;
import io.nats.client.Message;
import lombok.RequiredArgsConstructor;

/**
 * NATS Broker 适配器，桥接 ddd4j MQ SPI 与 jnats 客户端。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
        if (Objects.nonNull(natsMessage)) {
            return NatsMessageAcknowledgmentFactory.fromNatsMessage(natsMessage).orElse(null);
        }
        return message.nativeMessage(NatsMessageAcknowledgment.class);
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
