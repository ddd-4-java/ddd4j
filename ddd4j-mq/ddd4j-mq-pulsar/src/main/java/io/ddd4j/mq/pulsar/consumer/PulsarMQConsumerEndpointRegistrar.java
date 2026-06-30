package io.ddd4j.mq.pulsar.consumer;

import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.pulsar.ack.PulsarMessageAcknowledgment;
import io.ddd4j.mq.pulsar.spi.PulsarMQProperties;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQTagMatcher;
import org.apache.pulsar.client.api.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Programmatic Apache Pulsar consumer registrar.
 */
public class PulsarMQConsumerEndpointRegistrar {

    private final PulsarClient client;
    private final PulsarMQProperties properties;

    public PulsarMQConsumerEndpointRegistrar(PulsarClient client, PulsarMQProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    private static String messageIdString(MessageId id) {
        return java.util.Objects.isNull(id) ? null : id.toString();
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
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

    private void handleMessage(Consumer<byte[]> consumer, Message<byte[]> msg, MQListenerDefinition def, MQConsumerHandler handler) {
        try {
            String tag = msg.getProperty(MQMessages.HEADER_DESTINATION_TAG);
            if (!MQTagMatcher.match(tag, def.getTags())) {
                consumer.acknowledge(msg);
                return;
            }
            MQMessage<String> mq = toMessage(consumer, msg);
            PulsarMessageAcknowledgment ack = new PulsarMessageAcknowledgment(
                    consumer, msg, messageIdString(msg.getMessageId()), null);
            handler.handle(mq, ack);
        } catch (Exception ex) {
            try {
                consumer.negativeAcknowledge(msg);
            } catch (Exception ignore) {
            }
        }
    }

    private MQMessage<String> toMessage(Consumer<byte[]> consumer, Message<byte[]> msg) {
        Map<String, Object> headers = new HashMap<>();
        msg.getProperties().forEach((k, v) -> {
            if (java.util.Objects.nonNull(v)) {
                headers.put(k, v);
            }
        });
        headers.put(PulsarMessageAcknowledgment.HEADER_PULSAR_CONSUMER, consumer);
        headers.put(PulsarMessageAcknowledgment.HEADER_PULSAR_MESSAGE, msg);
        headers.put(PulsarMessageAcknowledgment.HEADER_PULSAR_MESSAGE_ID, messageIdString(msg.getMessageId()));
        return MQMessage.of(
                new String(msg.getValue(), StandardCharsets.UTF_8),
                headers,
                messageIdString(msg.getMessageId()),
                null,
                msg);
    }
}
