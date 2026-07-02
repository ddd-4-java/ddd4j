package io.ddd4j.mq.rocketmq;

import io.ddd4j.core.domain.event.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * RocketMQ event publisher.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RocketMQEventPublisher implements MQEventPublisher {

    private final MQProducer producer;
    private final Ddd4jMQProperties properties;
    private final MQEventSerialization serialization;

    public RocketMQEventPublisher(MQProducer producer, Ddd4jMQProperties properties, MQEventSerialization serialization) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    static Message toMessage(
            MQEvent event,
            MQDestination destination,
            Ddd4jMQProperties properties,
            MQEventSerialization serialization) {
        String topic = resolveTopic(event, destination, properties);
        String tag = firstText(destination.getTag(), event.getTag());
        byte[] body = serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8);
        Message message = Objects.isNull(tag)
                ? new Message(topic, body)
                : new Message(topic, tag, body);
        message.setKeys(firstText(event.getMsgId(), event.getTenantId()));
        put(message, MQMessages.HEADER_DESTINATION_TOPIC, topic);
        put(message, MQMessages.HEADER_DESTINATION_TAG, tag);
        put(message, MQMessages.HEADER_TENANT_ID, event.getTenantId());
        return message;
    }

    static String resolveTopic(MQEvent event, MQDestination destination, Ddd4jMQProperties properties) {
        String namespace = firstText(destination.getNamespace(), event.getNamespace(), properties.getNamespace());
        String topic = firstText(destination.getTopic(), event.getTopic(), properties.getDefaultTopic());
        String concat = firstText(event.getConcat(), ".");
        return Objects.isNull(namespace) ? topic : namespace + concat + topic;
    }

    private static void put(Message message, String key, String value) {
        if (Objects.nonNull(value)) {
            message.putUserProperty(key, value);
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
        try {
            producer.send(toMessage(event, destination, properties, serialization));
        } catch (Exception ex) {
            throw new IllegalStateException("Publish RocketMQ event failed", ex);
        }
    }
}
