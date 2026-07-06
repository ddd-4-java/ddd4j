package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.*;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.EndpointNaming;
import io.ddd4j.mq.listener.TagMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    private static boolean hasText(String s) {
        return Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }

    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        try {
            Channel channel = channelProvider.channel();
            String queue = EndpointNaming.queueName(definition);
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
                    RabbitAcknowledgment ack = new RabbitAcknowledgment(
                            channel,
                            envelope.getDeliveryTag(),
                            properties.getMessageId(),
                            properties.getCorrelationId());
                    try {
                        MessageConverter<byte[]> converter =
                                nativeBody -> toMessage(nativeBody, channel, envelope, properties);
                        if (!TagMatcher.match(resolveTag(properties, envelope, definition), definition.getTags())) {
                            ack.ackSingle();
                            return;
                        }
                        handler.handle(converter.convert(body), ack);
                    } catch (Exception ex) {
                        ack.nack(false, true);
                    }
                }
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Register RabbitMQ consumer failed", ex);
        }
    }

    private Message<String> toMessage(
            byte[] body,
            Channel channel,
            Envelope envelope,
            AMQP.BasicProperties properties) {
        Map<String, Object> headers = new HashMap<>();
        if (Objects.nonNull(properties.getHeaders())) {
            headers.putAll(properties.getHeaders());
        }
        headers.put(RabbitAcknowledgment.HEADER_RABBIT_CHANNEL, channel);
        headers.put(RabbitAcknowledgment.HEADER_RABBIT_DELIVERY_TAG, envelope.getDeliveryTag());
        headers.put(MessageHeaders.HEADER_DESTINATION_TOPIC, envelope.getExchange());
        headers.put(MessageHeaders.HEADER_DESTINATION_TAG, envelope.getRoutingKey());
        return Message.of(
                new String(body, StandardCharsets.UTF_8),
                headers,
                properties.getMessageId(),
                properties.getCorrelationId(),
                envelope);
    }

    private Set<String> routingKeys(ListenerDefinition definition) {
        String concat = EndpointNaming.resolveSeparator(definition);
        String base = hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic()
                : definition.getTopic();
        Set<String> includes = TagMatcher.findIncludes(definition.getTags());
        Set<String> keys = new LinkedHashSet<>();
        if (includes.isEmpty()) {
            keys.add(base);
            keys.add(base + concat + "*");
        } else {
            includes.forEach(tag -> keys.add(base + concat + tag));
        }
        return keys;
    }

    private String resolveTag(AMQP.BasicProperties properties, Envelope envelope, ListenerDefinition definition) {
        Object headerTag = Objects.isNull(properties.getHeaders())
                ? null
                : properties.getHeaders().get(MessageHeaders.HEADER_DESTINATION_TAG);
        if (Objects.nonNull(headerTag)) {
            return String.valueOf(headerTag);
        }
        String concat = EndpointNaming.resolveSeparator(definition);
        String prefix = hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic() + concat
                : definition.getTopic() + concat;
        String routingKey = envelope.getRoutingKey();
        return Objects.nonNull(routingKey) && routingKey.startsWith(prefix)
                ? routingKey.substring(prefix.length())
                : null;
    }
}
