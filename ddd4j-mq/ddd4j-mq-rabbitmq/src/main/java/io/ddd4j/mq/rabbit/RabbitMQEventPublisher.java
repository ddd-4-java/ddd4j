package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.serialization.EventSerialization;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RabbitMQ MQ event publisher.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RabbitMQEventPublisher implements MQEventPublisher {

    private final RabbitChannelProvider channelProvider;
    private final RabbitMQProperties rabbitProperties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;

    public RabbitMQEventPublisher(
            RabbitChannelProvider channelProvider,
            RabbitMQProperties rabbitProperties,
            MQProperties mqProperties,
            EventSerialization serialization) {
        this.channelProvider = Objects.requireNonNull(channelProvider, "channelProvider");
        this.rabbitProperties = Objects.requireNonNull(rabbitProperties, "rabbitProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    static String resolveRoutingKey(MQEvent event, Destination destination, MQProperties properties) {
        String namespace = StrKit.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : (StrKit.hasText(event.getNamespace()) ? event.getNamespace() : properties.getNamespace());
        String topic = StrKit.hasText(destination.getTopic())
                ? destination.getTopic()
                : (StrKit.hasText(event.getTopic()) ? event.getTopic() : properties.getDefaultTopic());
        String tag = StrKit.hasText(destination.getTag()) ? destination.getTag() : event.getTag();
        String concat = StrKit.hasText(event.getConcat()) ? event.getConcat() : ".";
        String base = StrKit.hasText(namespace) ? namespace + concat + topic : topic;
        return StrKit.hasText(tag) ? base + concat + tag : base;
    }

    private static void put(Map<String, Object> headers, String key, Object value) {
        if (Objects.nonNull(value)) {
            headers.put(key, value);
        }
    }

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        try {
            DestinationResolver.fillDefaults(event, mqProperties);
            Channel channel = channelProvider.channel();
            declareExchange(channel);
            String routingKey = resolveRoutingKey(event, destination, mqProperties);
            byte[] body = serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8);
            channel.basicPublish(rabbitProperties.getExchange(), routingKey, properties(event, destination), body);
        } catch (Exception ex) {
            throw new IllegalStateException("Publish RabbitMQ event failed", ex);
        }
    }

    private AMQP.BasicProperties properties(MQEvent event, Destination destination) {
        Map<String, Object> headers = new HashMap<>();
        put(headers, MessageHeaders.HEADER_DESTINATION_TOPIC, destination.getTopic());
        put(headers, MessageHeaders.HEADER_DESTINATION_TAG,
                StrKit.hasText(destination.getTag()) ? destination.getTag() : event.getTag());
        put(headers, MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
        return new AMQP.BasicProperties.Builder()
                .messageId(event.getMsgId())
                .correlationId(event.getMsgId())
                .headers(headers)
                .contentType("application/json")
                .build();
    }

    private void declareExchange(Channel channel) throws Exception {
        if (rabbitProperties.isAutoDeclare() && StrKit.hasText(rabbitProperties.getExchange())) {
            channel.exchangeDeclare(rabbitProperties.getExchange(), BuiltinExchangeType.TOPIC, rabbitProperties.isDurable());
        }
    }
}
