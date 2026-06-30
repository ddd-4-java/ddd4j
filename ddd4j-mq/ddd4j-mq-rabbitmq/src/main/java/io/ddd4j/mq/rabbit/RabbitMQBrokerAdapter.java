package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;

import java.util.Objects;

/**
 * RabbitMQ broker adapter for ddd4j MQ SPI.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RabbitMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final RabbitMQProperties rabbitProperties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final RabbitChannelProvider channelProvider;
    private final RabbitMQConsumerEndpointRegistrar consumerRegistrar;
    private Connection connection;

    public RabbitMQBrokerAdapter(RabbitMQProperties rabbitProperties, Ddd4jMQProperties mqProperties) {
        this(rabbitProperties, mqProperties, new JsonMQMessageSerialization(), null);
    }

    public RabbitMQBrokerAdapter(
            RabbitMQProperties rabbitProperties,
            Ddd4jMQProperties mqProperties,
            MQEventSerialization serialization,
            RabbitChannelProvider channelProvider) {
        this.rabbitProperties = Objects.requireNonNull(rabbitProperties, "rabbitProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.channelProvider = java.util.Objects.isNull(channelProvider) ? this::newChannel : channelProvider;
        this.consumerRegistrar = new RabbitMQConsumerEndpointRegistrar(this.channelProvider, rabbitProperties);
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.RABBIT;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new RabbitMQEventPublisher(channelProvider, rabbitProperties, java.util.Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        if (java.util.Objects.isNull(message)) {
            return null;
        }
        Object channel = message.header(RabbitMessageAcknowledgment.HEADER_RABBIT_CHANNEL);
        Object deliveryTag = message.header(RabbitMessageAcknowledgment.HEADER_RABBIT_DELIVERY_TAG);
        if (channel instanceof Channel rabbitChannel && deliveryTag instanceof Number tag) {
            return new RabbitMessageAcknowledgment(
                    rabbitChannel,
                    tag.longValue(),
                    message.getMessageId(),
                    message.getCorrelationId());
        }
        return null;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.RABBIT == configured;
    }

    @Override
    public void close() throws Exception {
        if (java.util.Objects.nonNull(connection) && connection.isOpen()) {
            connection.close();
        }
    }

    private synchronized Channel newChannel() throws Exception {
        if (java.util.Objects.isNull(connection) || !connection.isOpen()) {
            connection = rabbitProperties.connectionFactory().newConnection();
        }
        return connection.createChannel();
    }
}
