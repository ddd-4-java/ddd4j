package io.ddd4j.mq.kafka;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Kafka MQ event publisher.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class KafkaMQEventPublisher implements MQEventPublisher {

    private final Producer<String, String> producer;
    private final MQEventSerialization serialization;
    private final Ddd4jMQProperties mqProperties;

    public KafkaMQEventPublisher(
            KafkaMQProperties kafkaProperties,
            Ddd4jMQProperties mqProperties,
            MQEventSerialization serialization) {
        this(new KafkaProducer<>(kafkaProperties.producerProperties()), mqProperties, serialization);
    }

    public KafkaMQEventPublisher(
            Producer<String, String> producer,
            Ddd4jMQProperties mqProperties,
            MQEventSerialization serialization) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    static String resolveTopic(MQEvent event, MQDestination destination, Ddd4jMQProperties properties) {
        String namespace = firstText(destination.getNamespace(), event.getNamespace(), properties.getNamespace());
        String topic = firstText(destination.getTopic(), event.getTopic(), properties.getDefaultTopic());
        String concat = firstText(event.getConcat(), "_");
        return Objects.isNull(namespace) ? topic : namespace + concat + topic;
    }

    private static void addHeader(ProducerRecord<String, String> record, String key, String value) {
        if (Objects.nonNull(value)) {
            record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static String firstText(String... values) {
        if (Objects.isNull(values)) {
            return null;
        }
        for (String value : values) {
            if (Objects.nonNull(value) && !io.ddd4j.kit.lang.StrKit.isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        String topic = resolveTopic(event, destination);
        String tag = firstText(destination.getTag(), event.getTag());
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                tag,
                serialization.serialize(event));
        addHeader(record, MQMessages.HEADER_DESTINATION_TOPIC, topic);
        addHeader(record, MQMessages.HEADER_DESTINATION_TAG, tag);
        addHeader(record, MQMessages.HEADER_TENANT_ID, event.getTenantId());
        producer.send(record);
    }

    private String resolveTopic(MQEvent event, MQDestination destination) {
        return resolveTopic(event, destination, mqProperties);
    }
}
