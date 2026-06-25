package io.ddd4j.boot.cmpt.tdmq.spi;

import io.ddd4j.boot.cmpt.tdmq.acknowledgment.TdmqMessageAcknowledgment;
import io.ddd4j.boot.cmpt.tdmq.acknowledgment.TdmqMessageAcknowledgmentFactory;
import io.ddd4j.boot.cmpt.tdmq.client.TdmqClient;
import io.ddd4j.boot.cmpt.tdmq.consumer.TdmqMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.tdmq.publisher.TdmqMQEventPublisher;
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
 * 腾讯云 TDMQ Broker 适配器（占位 SPI  wiring，待 SDK 接入）。
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
