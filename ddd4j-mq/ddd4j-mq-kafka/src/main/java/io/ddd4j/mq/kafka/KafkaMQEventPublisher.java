package io.ddd4j.mq.kafka;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.event.MQEventSerialization;
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
    private final MQProperties mqProperties;

    public KafkaMQEventPublisher(
            KafkaMQProperties kafkaProperties,
            MQProperties mqProperties,
            MQEventSerialization serialization) {
        this(new KafkaProducer<>(kafkaProperties.producerProperties()), mqProperties, serialization);
    }

    public KafkaMQEventPublisher(
            Producer<String, String> producer,
            MQProperties mqProperties,
            MQEventSerialization serialization) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    /**
     * Kafka 物理地址拼接（使用下划线 {@code _} 作为默认分隔符，可被 event.concat 覆盖）。
     */
    static String resolveTopic(MQEvent event, Destination destination, MQProperties properties) {
        String namespace = StrKit.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : (StrKit.hasText(event.getNamespace()) ? event.getNamespace() : properties.getNamespace());
        String topic = StrKit.hasText(destination.getTopic())
                ? destination.getTopic()
                : (StrKit.hasText(event.getTopic()) ? event.getTopic() : properties.getDefaultTopic());
        String concat = StrKit.hasText(event.getConcat()) ? event.getConcat() : "_";
        return StrKit.hasText(namespace) ? namespace + concat + topic : topic;
    }

    private static void addHeader(ProducerRecord<String, String> record, String key, String value) {
        if (Objects.nonNull(value)) {
            record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        DestinationResolver.fillDefaults(event, mqProperties);
        String topic = resolveTopic(event, destination);
        String tag = StrKit.hasText(destination.getTag())
                ? destination.getTag()
                : event.getTag();
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                tag,
                serialization.serialize(event));
        addHeader(record, MessageHeaders.HEADER_DESTINATION_TOPIC, topic);
        addHeader(record, MessageHeaders.HEADER_DESTINATION_TAG, tag);
        addHeader(record, MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
        producer.send(record);
    }

    private String resolveTopic(MQEvent event, Destination destination) {
        return resolveTopic(event, destination, mqProperties);
    }
}
