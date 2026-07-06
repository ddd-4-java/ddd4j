package io.ddd4j.mq.pulsar.consumer;

import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.pulsar.ack.PulsarAcknowledgment;
import io.ddd4j.mq.pulsar.spi.PulsarMQProperties;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.TagMatcher;
import org.apache.pulsar.client.api.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Apache Pulsar 消费者端点注册器（编程式注册）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PulsarMQConsumerEndpointRegistrar {

    private final PulsarClient client;
    private final PulsarMQProperties properties;

    public PulsarMQConsumerEndpointRegistrar(PulsarClient client, PulsarMQProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    private static String messageIdString(MessageId id) {
        return Objects.isNull(id) ? null : id.toString();
    }

    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        try {
            String topic = properties.physicalTopic(definition.getTopic(), null);
            client.newConsumer(Schema.BYTES)
                    .topic(topic)
                    .subscriptionName(properties.getSubscriptionName() + "-" + definition.bindingName())
                    .subscriptionType(SubscriptionType.valueOf(properties.getSubscriptionType()))
                    .negativeAckRedeliveryDelay(properties.getNegativeAckRedeliveryDelayMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .messageListener((consumer, msg) -> {
                        handleMessage(consumer, msg, definition, handler);
                    })
                    .subscribe();
        } catch (Exception ex) {
            throw new IllegalStateException("Register Pulsar consumer failed", ex);
        }
    }

    private void handleMessage(Consumer<byte[]> consumer, Message<byte[]> msg, ListenerDefinition def, ConsumerHandler handler) {
        try {
            String tag = msg.getProperty(MessageHeaders.HEADER_DESTINATION_TAG);
            if (!TagMatcher.match(tag, def.getTags())) {
                consumer.acknowledge(msg);
                return;
            }
            MessageConverter<Message<byte[]>> converter = nativeMsg -> toMessage(consumer, nativeMsg);
            Message<?> mq = converter.convert(msg);
            PulsarAcknowledgment ack = new PulsarAcknowledgment(
                    consumer, msg, messageIdString(msg.getMessageId()), null);
            handler.handle(mq, ack);
        } catch (Exception ex) {
            try {
                consumer.negativeAcknowledge(msg);
            } catch (Exception ignore) {
            }
        }
    }

    private Message<String> toMessage(Consumer<byte[]> consumer, Message<byte[]> msg) {
        Map<String, Object> headers = new HashMap<>();
        msg.getProperties().forEach((k, v) -> {
            if (Objects.nonNull(v)) {
                headers.put(k, v);
            }
        });
        headers.put(PulsarAcknowledgment.HEADER_PULSAR_CONSUMER, consumer);
        headers.put(PulsarAcknowledgment.HEADER_PULSAR_MESSAGE, msg);
        headers.put(PulsarAcknowledgment.HEADER_PULSAR_MESSAGE_ID, messageIdString(msg.getMessageId()));
        return Message.of(
                new String(msg.getValue(), StandardCharsets.UTF_8),
                headers,
                messageIdString(msg.getMessageId()),
                null,
                msg);
    }
}
