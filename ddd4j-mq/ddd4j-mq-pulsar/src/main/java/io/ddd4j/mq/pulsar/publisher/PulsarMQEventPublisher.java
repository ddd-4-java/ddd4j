package io.ddd4j.mq.pulsar.publisher;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.pulsar.spi.PulsarMQProperties;
import io.ddd4j.mq.event.MQEventSerialization;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.TypedMessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Apache Pulsar 事件发布器（纯 Java，原生 pulsar-client）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PulsarMQEventPublisher implements MQEventPublisher {

    private final PulsarClient client;
    private final PulsarMQProperties properties;
    private final MQProperties mqProperties;
    private final MQEventSerialization serialization;
    private volatile Producer<byte[]> producer;

    public PulsarMQEventPublisher(PulsarClient client, PulsarMQProperties properties,
                                  MQProperties mqProperties, MQEventSerialization serialization) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        try {
            DestinationResolver.fillDefaults(event, mqProperties);
            String topic = properties.physicalTopic(
                    StrKit.hasText(destination.getTopic())
                            ? destination.getTopic()
                            : (StrKit.hasText(event.getTopic()) ? event.getTopic() : "ddd4j.default.topic"),
                    StrKit.hasText(destination.getTag()) ? destination.getTag() : event.getTag());
            Producer<byte[]> p = producer(topic);
            TypedMessageBuilder<byte[]> builder = p.newMessage()
                    .value(serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8))
                    .property(MessageHeaders.HEADER_DESTINATION_TOPIC, destination.getTopic());
            if (Objects.nonNull(event.getMsgId())) {
                builder.property(MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
            }
            if (Objects.nonNull(event.getTenantId())) {
                builder.property(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
            }
            if (Objects.nonNull(event.getTag())) {
                builder.property(MessageHeaders.HEADER_DESTINATION_TAG, event.getTag());
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
