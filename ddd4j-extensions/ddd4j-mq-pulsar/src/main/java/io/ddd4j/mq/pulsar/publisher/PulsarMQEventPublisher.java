package io.ddd4j.mq.pulsar.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.core.utils.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 基于原生 PulsarClient 的领域事件发布实现（JDK8 兼容）。 */
public class PulsarMQEventPublisher implements MQEventPublisher {
    private final PulsarClient pulsarClient;
    private final Ddd4jMQProperties properties;
    private final Map<String, Producer<String>> producers = new ConcurrentHashMap<String, Producer<String>>();
    public PulsarMQEventPublisher(PulsarClient pulsarClient, Ddd4jMQProperties properties) { this.pulsarClient = Objects.requireNonNull(pulsarClient, "pulsarClient"); this.properties = Objects.requireNonNull(properties, "properties"); }
    @Override public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event"); Objects.requireNonNull(destination, "destination");
        if (!StringUtils.hasText(event.getTopic())) event.setTopic(properties.getDefaultTopic());
        if (!StringUtils.hasText(event.getNamespace())) event.setNamespace(properties.getNamespace());
        if (event.getMsgId() == null) event.setMsgId(String.valueOf(System.currentTimeMillis()));
        String topic = buildTopic(destination, event.getTag());
        try { producer(topic).send(JsonKit.toJson(event)); }
        catch (PulsarClientException exception) { throw new IllegalStateException("Failed to publish Pulsar event to " + topic, exception); }
    }
    private Producer<String> producer(String topic) throws PulsarClientException {
        Producer<String> existing = producers.get(topic); if (existing != null) return existing;
        Producer<String> created = pulsarClient.newProducer(Schema.STRING).topic(topic).create();
        Producer<String> previous = producers.putIfAbsent(topic, created);
        if (previous != null) { created.close(); return previous; }
        return created;
    }
    private String buildTopic(MQDestination destination, String eventTag) {
        String namespace = StringUtils.hasText(destination.namespace()) ? destination.namespace() : properties.getNamespace();
        String topic = StringUtils.hasText(destination.topic()) ? destination.topic() : properties.getDefaultTopic();
        String tag = StringUtils.hasText(destination.tag()) ? destination.tag() : eventTag;
        String physical = StringUtils.hasText(namespace) ? namespace + "." + topic : topic;
        return StringUtils.hasText(tag) ? physical + "." + tag : physical;
    }
}
