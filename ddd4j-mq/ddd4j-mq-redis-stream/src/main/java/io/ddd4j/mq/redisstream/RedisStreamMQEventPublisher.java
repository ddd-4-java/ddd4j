package io.ddd4j.mq.redisstream;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.redisstream.jedis.JedisRedisStreamOperations;
import redis.clients.jedis.UnifiedJedis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Redis Stream MQ event publisher.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedisStreamMQEventPublisher implements MQEventPublisher {

    public static final String FIELD_PAYLOAD = "payload";

    private final RedisStreamOperations operations;
    private final Ddd4jMQProperties properties;
    private final MQEventSerialization serialization;

    public RedisStreamMQEventPublisher(UnifiedJedis jedis, Ddd4jMQProperties properties, MQEventSerialization serialization) {
        this(new JedisRedisStreamOperations(jedis), properties, serialization);
    }

    public RedisStreamMQEventPublisher(RedisStreamOperations operations, Ddd4jMQProperties properties, MQEventSerialization serialization) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        operations.add(resolveStream(event, destination, properties), fields(event, destination));
    }

    static String resolveStream(MQEvent event, MQDestination destination, Ddd4jMQProperties properties) {
        String namespace = firstText(destination.getNamespace(), event.getNamespace(), properties.getNamespace());
        String topic = firstText(destination.getTopic(), event.getTopic(), properties.getDefaultTopic());
        String tag = firstText(destination.getTag(), event.getTag());
        String concat = firstText(event.getConcat(), ".");
        String base = java.util.Objects.isNull(namespace) ? topic : namespace + concat + topic;
        return java.util.Objects.isNull(tag) ? base : base + concat + tag;
    }

    private Map<String, String> fields(MQEvent event, MQDestination destination) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_PAYLOAD, serialization.serialize(event).toString());
        put(fields, MQMessages.HEADER_DESTINATION_TOPIC, destination.getTopic());
        put(fields, MQMessages.HEADER_DESTINATION_TAG, firstText(destination.getTag(), event.getTag()));
        put(fields, MQMessages.HEADER_TENANT_ID, event.getTenantId());
        put(fields, MQMessages.HEADER_MESSAGE_ID, event.getMsgId());
        return fields;
    }

    private static void put(Map<String, String> fields, String key, String value) {
        if (java.util.Objects.nonNull(value)) {
            fields.put(key, value);
        }
    }

    private static String firstText(String... values) {
        if (java.util.Objects.isNull(values)) {
            return null;
        }
        for (String value : values) {
            if (java.util.Objects.nonNull(value) && !io.ddd4j.kit.lang.StrKit.isBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
