package io.ddd4j.mq.activemq.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.activemq.ack.ActiveMQMessageAcknowledgment;
import io.ddd4j.mq.activemq.ack.ActiveMQMessageAcknowledgmentFactory;
import io.ddd4j.mq.activemq.consumer.ActiveMQConsumerEndpointRegistrar;
import io.ddd4j.mq.activemq.publisher.ActiveMQEventPublisher;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;

/**
 * ActiveMQ Artemis Broker 适配器，桥接 ddd4j MQ SPI 与 Spring JMS。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class ActiveMQBrokerAdapter implements MQBrokerAdapter {

    private final JmsTemplate jmsTemplate;
    private final Ddd4jMQProperties properties;
    private final ActiveMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ACTIVEMQ;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new ActiveMQEventPublisher(jmsTemplate, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 2.0.x：直接基于纯 Java MQMessage 解析（jakarta.jms.Message 通过 nativeMessage 逃生口传入）
        ActiveMQMessageAcknowledgment activeMqAck = message.nativeMessage(ActiveMQMessageAcknowledgment.class);
        if (activeMqAck != null) {
            return activeMqAck;
        }
        return ActiveMQMessageAcknowledgmentFactory.from(message).orElse(null);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.ACTIVEMQ == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
