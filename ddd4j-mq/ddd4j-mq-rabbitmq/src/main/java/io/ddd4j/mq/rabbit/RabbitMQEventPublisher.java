package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;

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
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;

    public RabbitMQEventPublisher(
            RabbitChannelProvider channelProvider,
            RabbitMQProperties rabbitProperties,
            Ddd4jMQProperties mqProperties,
            MQEventSerialization serialization) {
        this.channelProvider = Objects.requireNonNull(channelProvider, "channelProvider");
        this.rabbitProperties = Objects.requireNonNull(rabbitProperties, "rabbitProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    static String resolveRoutingKey(MQEvent event, MQDestination destination, Ddd4jMQProperties properties) {
        String namespace = firstText(destination.getNamespace(), event.getNamespace(), properties.getNamespace());
        String topic = firstText(destination.getTopic(), event.getTopic(), properties.getDefaultTopic());
        String tag = firstText(destination.getTag(), event.getTag());
        String concat = firstText(event.getConcat(), ".");
        String base = java.util.Objects.isNull(namespace) ? topic : namespace + concat + topic;
        return java.util.Objects.isNull(tag) ? base : base + concat + tag;
    }

    private static void put(Map<String, Object> headers, String key, Object value) {
        if (java.util.Objects.nonNull(value)) {
            headers.put(key, value);
        }
    }

    private static String firstText(String... values) {
        if (java.util.Objects.isNull(values)) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasText(String s) {
        return java.util.Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        try {
            Channel channel = channelProvider.channel();
            declareExchange(channel);
            String routingKey = resolveRoutingKey(event, destination, mqProperties);
            byte[] body = serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8);
            channel.basicPublish(rabbitProperties.getExchange(), routingKey, properties(event, destination), body);
        } catch (Exception ex) {
            throw new IllegalStateException("Publish RabbitMQ event failed", ex);
        }
    }

    private AMQP.BasicProperties properties(MQEvent event, MQDestination destination) {
        Map<String, Object> headers = new HashMap<>();
        put(headers, MQMessages.HEADER_DESTINATION_TOPIC, destination.getTopic());
        put(headers, MQMessages.HEADER_DESTINATION_TAG, firstText(destination.getTag(), event.getTag()));
        put(headers, MQMessages.HEADER_TENANT_ID, event.getTenantId());
        return new AMQP.BasicProperties.Builder()
                .messageId(event.getMsgId())
                .correlationId(event.getMsgId())
                .headers(headers)
                .contentType("application/json")
                .build();
    }

    private void declareExchange(Channel channel) throws Exception {
        if (rabbitProperties.isAutoDeclare() && hasText(rabbitProperties.getExchange())) {
            channel.exchangeDeclare(rabbitProperties.getExchange(), BuiltinExchangeType.TOPIC, rabbitProperties.isDurable());
        }
    }
}
