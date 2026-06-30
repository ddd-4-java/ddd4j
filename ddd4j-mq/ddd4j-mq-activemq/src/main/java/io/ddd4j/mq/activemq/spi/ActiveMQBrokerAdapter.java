package io.ddd4j.mq.activemq.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.activemq.ack.ActiveMQMessageAcknowledgment;
import io.ddd4j.mq.activemq.ack.ActiveMQMessageAcknowledgmentFactory;
import io.ddd4j.mq.activemq.consumer.ActiveMQConsumerEndpointRegistrar;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;

/**
 * ActiveMQ Artemis Broker 适配器（纯 Java，零 Spring 依赖）。
 * <p>Publisher 由 ddd4j-boot-mq-activemq 的 AutoConfiguration 直接创建 Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class ActiveMQBrokerAdapter implements MQBrokerAdapter {

    private final Ddd4jMQProperties properties;
    private final ActiveMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ACTIVEMQ;
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
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

    public Ddd4jMQProperties properties() {
        return properties;
    }
}
