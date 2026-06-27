package io.ddd4j.mq.ons.spi;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.mq.ons.ack.OnsMessageAcknowledgment;
import io.ddd4j.mq.ons.ack.OnsMessageAcknowledgmentFactory;
import io.ddd4j.mq.ons.consumer.OnsMQConsumerEndpointRegistrar;
import io.ddd4j.mq.ons.publisher.OnsMQEventPublisher;
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
 * 阿里云 ONS Broker 适配器，桥接 ddd4j MQ SPI 与 ons-client（Rocket 兼容）。
 */
@RequiredArgsConstructor
public class OnsMQBrokerAdapter implements MQBrokerAdapter {

    private final Producer producer;
    private final Ddd4jMQProperties properties;
    private final OnsMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ONS;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new OnsMQEventPublisher(producer, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 逻辑块：优先从 ONS 原生 Message 解析确认
        Message onsMessage = message.nativeMessage(Message.class);
        if (onsMessage != null) {
            return OnsMessageAcknowledgmentFactory.fromOnsMessage(onsMessage)
                    .map(ack -> (MessageAcknowledgment) ack)
                    .orElse(null);
        }
        OnsMessageAcknowledgment onsAck = message.nativeMessage(OnsMessageAcknowledgment.class);
        return onsAck;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.ONS == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
