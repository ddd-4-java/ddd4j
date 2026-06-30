package io.ddd4j.mq.rabbit.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.rabbit.ack.AmqpMessageAcknowledgmentFactory;
import io.ddd4j.mq.rabbit.consumer.RabbitMQConsumerEndpointRegistrar;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;

/**
 * RabbitMQ Broker 适配器，桥接 ddd4j MQ SPI 与 Spring AMQP。
 * <p>2.0.x 重构：Publisher 由 ddd4j-boot-mq-rabbitmq 的 AutoConfiguration 直接创建 Bean，
 * 本类不再引用 Spring 客户端库。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class RabbitMQBrokerAdapter implements MQBrokerAdapter {

    private final Ddd4jMQProperties properties;
    private final RabbitMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.RABBIT;
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 2.0.x：直接基于纯 Java MQMessage 解析（headers 中携带 Channel/deliveryTag）
        return AmqpMessageAcknowledgmentFactory.from(message).orElse(null);
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
