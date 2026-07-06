package io.ddd4j.mq.rocketmq;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.serialization.EventSerialization;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * RocketMQ event publisher.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RocketEventPublisher implements EventPublisher {

    private final MQProducer producer;
    private final MQProperties properties;
    private final EventSerialization serialization;

    public RocketEventPublisher(MQProducer producer, MQProperties properties, EventSerialization serialization) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    static Message toMessage(
            MQEvent event,
            Destination destination,
            MQProperties properties,
            EventSerialization serialization) {
        String topic = resolveTopic(event, destination, properties);
        String tag = StrKit.hasText(destination.getTag()) ? destination.getTag() : event.getTag();
        byte[] body = serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8);
        Message message = StrKit.hasText(tag)
                ? new Message(topic, tag, body)
                : new Message(topic, body);
        message.setKeys(StrKit.hasText(event.getMsgId()) ? event.getMsgId() : event.getTenantId());
        put(message, MessageHeaders.HEADER_DESTINATION_TOPIC, topic);
        put(message, MessageHeaders.HEADER_DESTINATION_TAG, tag);
        put(message, MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
        return message;
    }

    static String resolveTopic(MQEvent event, Destination destination, MQProperties properties) {
        String namespace = StrKit.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : (StrKit.hasText(event.getNamespace()) ? event.getNamespace() : properties.getNamespace());
        String topic = StrKit.hasText(destination.getTopic())
                ? destination.getTopic()
                : (StrKit.hasText(event.getTopic()) ? event.getTopic() : properties.getDefaultTopic());
        String concat = StrKit.hasText(event.getConcat()) ? event.getConcat() : ".";
        return StrKit.hasText(namespace) ? namespace + concat + topic : topic;
    }

    private static void put(Message message, String key, String value) {
        if (Objects.nonNull(value)) {
            message.putUserProperty(key, value);
        }
    }

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        try {
            DestinationResolver.fillDefaults(event, properties);
            producer.send(toMessage(event, destination, properties, serialization));
        } catch (Exception ex) {
            throw new IllegalStateException("Publish RocketMQ event failed", ex);
        }
    }
}
