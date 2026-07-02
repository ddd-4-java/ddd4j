package io.ddd4j.mq.pulsar.publisher;

import io.ddd4j.core.domain.event.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.pulsar.spi.PulsarMQProperties;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.TypedMessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Apache Pulsar event publisher (pure Java, native pulsar-client).
 */
public class PulsarMQEventPublisher implements MQEventPublisher {

    private final PulsarClient client;
    private final PulsarMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private volatile Producer<byte[]> producer;

    public PulsarMQEventPublisher(PulsarClient client, PulsarMQProperties properties,
                                  Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    private static String firstText(String... values) {
        if (Objects.isNull(values)) {
            return null;
        }
        for (String v : values) {
            if (Objects.nonNull(v) && !io.ddd4j.kit.lang.StrKit.isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        try {
            String topic = properties.physicalTopic(
                    firstText(destination.getTopic(), event.getTopic(), "ddd4j.default.topic"),
                    firstText(destination.getTag(), event.getTag()));
            Producer<byte[]> p = producer(topic);
            TypedMessageBuilder<byte[]> builder = p.newMessage()
                    .value(serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8))
                    .property(MQMessages.HEADER_DESTINATION_TOPIC, destination.getTopic());
            if (Objects.nonNull(event.getMsgId())) {
                builder.property(MQMessages.HEADER_MESSAGE_ID, event.getMsgId());
            }
            if (Objects.nonNull(event.getTenantId())) {
                builder.property(MQMessages.HEADER_TENANT_ID, event.getTenantId());
            }
            if (Objects.nonNull(event.getTag())) {
                builder.property(MQMessages.HEADER_DESTINATION_TAG, event.getTag());
            }
            builder.sendAsync();
        } catch (Exception ex) {
            throw new IllegalStateException("Publish Pulsar event failed", ex);
        }
    }

    private synchronized Producer<byte[]> producer(String topic) throws Exception {
        Producer<byte[]> p = producer;
        if (Objects.isNull(p)) {
            p = client.newProducer(Schema.BYTES)
                    .topic(topic)
                    .batchingMaxPublishDelay(10, TimeUnit.MILLISECONDS)
                    .create();
            this.producer = p;
        }
        return p;
    }
}
