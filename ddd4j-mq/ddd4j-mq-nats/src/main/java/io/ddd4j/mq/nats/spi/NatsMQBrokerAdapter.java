package io.ddd4j.mq.nats.spi;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.nats.ack.NatsAcknowledgment;
import io.ddd4j.mq.nats.ack.NatsAcknowledgmentFactory;
import io.ddd4j.mq.nats.consumer.NatsMQConsumerEndpointRegistrar;
import io.ddd4j.mq.nats.publisher.NatsEventPublisher;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.spi.BrokerAdapter;
import io.nats.client.Connection;
import io.nats.client.Message;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * NATS Broker 适配器，桥接 ddd4j MQ SPI 与 jnats 客户端。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class NatsBrokerAdapter implements BrokerAdapter {

    private final Connection connection;
    private final MQProperties properties;
    private final NatsMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public BrokerType brokerType() {
        return BrokerType.NATS;
    }

    @Override
    public EventPublisher createPublisher(MQProperties props) {
        return new NatsEventPublisher(connection, props);
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        // 逻辑块：优先从 NATS 原生 Message 解析 JetStream 确认
        Message natsMessage = message.nativeMessage(Message.class);
        if (Objects.nonNull(natsMessage)) {
            return NatsAcknowledgmentFactory.fromNatsMessage(natsMessage).orElse(null);
        }
        return message.nativeMessage(NatsAcknowledgment.class);
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.NATS == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public MQProperties properties() {
        return properties;
    }
}
