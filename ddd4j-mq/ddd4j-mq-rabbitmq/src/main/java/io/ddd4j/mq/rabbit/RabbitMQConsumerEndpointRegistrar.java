package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.ddd4j.mq.registry.MQTagMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Programmatic RabbitMQ consumer registrar.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RabbitMQConsumerEndpointRegistrar {

    private final RabbitChannelProvider channelProvider;
    private final RabbitMQProperties rabbitProperties;

    public RabbitMQConsumerEndpointRegistrar(RabbitChannelProvider channelProvider, RabbitMQProperties rabbitProperties) {
        this.channelProvider = Objects.requireNonNull(channelProvider, "channelProvider");
        this.rabbitProperties = Objects.requireNonNull(rabbitProperties, "rabbitProperties");
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        try {
            Channel channel = channelProvider.channel();
            String queue = MQListenerEndpointNaming.queueName(definition);
            if (rabbitProperties.isAutoDeclare()) {
                channel.exchangeDeclare(rabbitProperties.getExchange(), BuiltinExchangeType.TOPIC, rabbitProperties.isDurable());
                channel.queueDeclare(queue, rabbitProperties.isDurable(), false, false, null);
                for (String routingKey : routingKeys(definition)) {
                    channel.queueBind(queue, rabbitProperties.getExchange(), routingKey);
                }
            }
            channel.basicConsume(queue, false, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body) throws IOException {
                    RabbitMessageAcknowledgment ack = new RabbitMessageAcknowledgment(
                            channel,
                            envelope.getDeliveryTag(),
                            properties.getMessageId(),
                            properties.getCorrelationId());
                    try {
                        if (!MQTagMatcher.match(resolveTag(properties, envelope, definition), definition.getTags())) {
                            ack.ackSingle();
                            return;
                        }
                        handler.handle(toMessage(body, channel, envelope, properties), ack);
                    } catch (Exception ex) {
                        ack.nack(false, true);
                    }
                }
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Register RabbitMQ consumer failed", ex);
        }
    }

    private MQMessage<String> toMessage(
            byte[] body,
            Channel channel,
            Envelope envelope,
            AMQP.BasicProperties properties) {
        Map<String, Object> headers = new HashMap<>();
        if (java.util.Objects.nonNull(properties.getHeaders())) {
            headers.putAll(properties.getHeaders());
        }
        headers.put(RabbitMessageAcknowledgment.HEADER_RABBIT_CHANNEL, channel);
        headers.put(RabbitMessageAcknowledgment.HEADER_RABBIT_DELIVERY_TAG, envelope.getDeliveryTag());
        headers.put(MQMessages.HEADER_DESTINATION_TOPIC, envelope.getExchange());
        headers.put(MQMessages.HEADER_DESTINATION_TAG, envelope.getRoutingKey());
        return MQMessage.of(
                new String(body, StandardCharsets.UTF_8),
                headers,
                properties.getMessageId(),
                properties.getCorrelationId(),
                envelope);
    }

    private Set<String> routingKeys(MQListenerDefinition definition) {
        String concat = MQListenerEndpointNaming.resolveConcat(definition);
        String base = hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic()
                : definition.getTopic();
        Set<String> includes = MQTagMatcher.findIncludes(definition.getTags());
        Set<String> keys = new LinkedHashSet<>();
        if (includes.isEmpty()) {
            keys.add(base);
            keys.add(base + concat + "*");
        } else {
            includes.forEach(tag -> keys.add(base + concat + tag));
        }
        return keys;
    }

    private String resolveTag(AMQP.BasicProperties properties, Envelope envelope, MQListenerDefinition definition) {
        Object headerTag = java.util.Objects.isNull(properties.getHeaders())
                ? null
                : properties.getHeaders().get(MQMessages.HEADER_DESTINATION_TAG);
        if (java.util.Objects.nonNull(headerTag)) {
            return String.valueOf(headerTag);
        }
        String concat = MQListenerEndpointNaming.resolveConcat(definition);
        String prefix = hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic() + concat
                : definition.getTopic() + concat;
        String routingKey = envelope.getRoutingKey();
        return java.util.Objects.nonNull(routingKey) && routingKey.startsWith(prefix)
                ? routingKey.substring(prefix.length())
                : null;
    }

    private static boolean hasText(String s) {
        return java.util.Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }
}
