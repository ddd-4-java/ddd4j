package io.ddd4j.mq.tdmq.spi;

import io.ddd4j.mq.tdmq.ack.TdmqMessageAcknowledgment;
import io.ddd4j.mq.tdmq.ack.TdmqMessageAcknowledgmentFactory;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import io.ddd4j.mq.tdmq.consumer.TdmqMQConsumerEndpointRegistrar;
import io.ddd4j.mq.tdmq.publisher.TdmqMQEventPublisher;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;

/**
 * 腾讯云 TDMQ Broker 适配器（占位 SPI  wiring，待 SDK 接入）。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class TdmqMQBrokerAdapter implements MQBrokerAdapter {

    private final TdmqClient tdmqClient;
    private final Ddd4jMQProperties properties;
    private final TdmqMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.TDMQ;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new TdmqMQEventPublisher(tdmqClient, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        return TdmqMessageAcknowledgmentFactory.from(message)
                .map(ack -> (MessageAcknowledgment) ack)
                .orElseGet(TdmqMessageAcknowledgment::noOp);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.TDMQ == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
