package io.ddd4j.mq.redisstream;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.redisstream.jedis.JedisRedisStreamOperations;
import io.ddd4j.mq.serialization.EventSerialization;
import redis.clients.jedis.UnifiedJedis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Redis Stream MQ event publisher.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedisStreamEventPublisher implements EventPublisher {

    public static final String FIELD_PAYLOAD = "payload";

    private final RedisStreamOperations operations;
    private final MQProperties properties;
    private final EventSerialization serialization;

    public RedisStreamEventPublisher(UnifiedJedis jedis, MQProperties properties, EventSerialization serialization) {
        this(new JedisRedisStreamOperations(jedis), properties, serialization);
    }

    public RedisStreamEventPublisher(RedisStreamOperations operations, MQProperties properties, EventSerialization serialization) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    static String resolveStream(MQEvent event, Destination destination, MQProperties properties) {
        String namespace = StrKit.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : (StrKit.hasText(event.getNamespace()) ? event.getNamespace() : properties.getNamespace());
        String topic = StrKit.hasText(destination.getTopic())
                ? destination.getTopic()
                : (StrKit.hasText(event.getTopic()) ? event.getTopic() : properties.getDefaultTopic());
        String tag = StrKit.hasText(destination.getTag()) ? destination.getTag() : event.getTag();
        String concat = StrKit.hasText(event.getConcat()) ? event.getConcat() : ".";
        String base = StrKit.hasText(namespace) ? namespace + concat + topic : topic;
        return StrKit.hasText(tag) ? base + concat + tag : base;
    }

    private static void put(Map<String, String> fields, String key, String value) {
        if (Objects.nonNull(value)) {
            fields.put(key, value);
        }
    }

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        DestinationResolver.fillDefaults(event, properties);
        operations.add(resolveStream(event, destination, properties), fields(event, destination));
    }

    private Map<String, String> fields(MQEvent event, Destination destination) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_PAYLOAD, serialization.serialize(event).toString());
        put(fields, MessageHeaders.HEADER_DESTINATION_TOPIC, destination.getTopic());
        put(fields, MessageHeaders.HEADER_DESTINATION_TAG,
                StrKit.hasText(destination.getTag()) ? destination.getTag() : event.getTag());
        put(fields, MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
        put(fields, MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
        return fields;
    }
}
