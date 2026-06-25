package io.ddd4j.boot.cmpt.rabbit.spi;

import io.ddd4j.boot.cmpt.rabbit.acknowledgment.AmqpMessageAcknowledgment;
import io.ddd4j.boot.cmpt.rabbit.acknowledgment.AmqpMessageAcknowledgmentFactory;
import io.ddd4j.boot.cmpt.rabbit.consumer.RabbitMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.rabbit.publisher.RabbitMQEventPublisher;
import io.ddd4j.boot.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.consume.MQConsumerHandler;
import io.ddd4j.boot.mq.contract.MQMessage;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.registry.MQBrokerType;
import io.ddd4j.boot.mq.registry.MQListenerDefinition;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.Message;

/**
 * RabbitMQ Broker 适配器，桥接 ddd4j MQ SPI 与 Spring AMQP。
 */
@RequiredArgsConstructor
public class RabbitMQBrokerAdapter implements MQBrokerAdapter {

    private final RabbitTemplate rabbitTemplate;
    private final Ddd4jMQProperties properties;
    private final RabbitMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.RABBIT;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new RabbitMQEventPublisher(rabbitTemplate, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 逻辑块：优先从 Spring Message 原生对象解析 AMQP 确认头
        Message<?> springMessage = message.nativeMessage(Message.class);
        if (springMessage != null) {
            return AmqpMessageAcknowledgmentFactory.fromSpringMessage(springMessage)
                    .map(ack -> (MessageAcknowledgment) ack)
                    .orElse(null);
        }
        AmqpMessageAcknowledgment amqpAck = message.nativeMessage(AmqpMessageAcknowledgment.class);
        return amqpAck;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.RABBIT == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
