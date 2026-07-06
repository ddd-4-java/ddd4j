package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;

import java.util.Objects;

/**
 * RabbitMQ broker adapter for ddd4j MQ SPI.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RabbitBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final RabbitMQProperties rabbitProperties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;
    private final RabbitChannelProvider channelProvider;
    private final RabbitMQConsumerEndpointRegistrar consumerRegistrar;
    private Connection connection;

    public RabbitBrokerAdapter(RabbitMQProperties rabbitProperties, MQProperties mqProperties) {
        this(rabbitProperties, mqProperties, new JsonSerialization(), null);
    }

    public RabbitBrokerAdapter(
            RabbitMQProperties rabbitProperties,
            MQProperties mqProperties,
            EventSerialization serialization,
            RabbitChannelProvider channelProvider) {
        this.rabbitProperties = Objects.requireNonNull(rabbitProperties, "rabbitProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.channelProvider = Objects.isNull(channelProvider) ? this::newChannel : channelProvider;
        this.consumerRegistrar = new RabbitMQConsumerEndpointRegistrar(this.channelProvider, rabbitProperties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.RABBIT;
    }

    @Override
    public EventPublisher createPublisher(MQProperties props) {
        return new RabbitEventPublisher(channelProvider, rabbitProperties, Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        Object channel = message.header(RabbitAcknowledgment.HEADER_RABBIT_CHANNEL);
        Object deliveryTag = message.header(RabbitAcknowledgment.HEADER_RABBIT_DELIVERY_TAG);
        if (channel instanceof Channel rabbitChannel && deliveryTag instanceof Number tag) {
            return new RabbitAcknowledgment(
                    rabbitChannel,
                    tag.longValue(),
                    message.getMessageId(),
                    message.getCorrelationId());
        }
        return null;
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.RABBIT == configured;
    }

    @Override
    public void close() throws Exception {
        if (Objects.nonNull(connection) && connection.isOpen()) {
            connection.close();
        }
    }

    private synchronized Channel newChannel() throws Exception {
        if (Objects.isNull(connection) || !connection.isOpen()) {
            connection = rabbitProperties.connectionFactory().newConnection();
        }
        return connection.createChannel();
    }
}
