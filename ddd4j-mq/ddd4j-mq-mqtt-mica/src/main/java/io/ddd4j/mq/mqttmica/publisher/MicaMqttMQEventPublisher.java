package io.ddd4j.mq.mqttmica.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.mqttmica.spi.MicaMqttProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * mica-mqtt AIO event publisher (pure Java).
 */
public class MicaMqttMQEventPublisher implements MQEventPublisher {

    private final MqttClient client;
    private final MicaMqttProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;

    public MicaMqttMQEventPublisher(MqttClient client, MicaMqttProperties properties,
                                    Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        try {
            String topic = firstText(destination.getTopic(), event.getTopic(), "ddd4j/default/topic");
            String ns = destination.getNamespace();
            String tag = destination.getTag();
            String physical = (ns == null || ns.isBlank() ? "" : ns + "/") + topic
                    + (tag == null || tag.isBlank() ? "" : "/" + tag);
            client.publish(physical,
                    serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8),
                    properties.mqttQoS());
        } catch (Exception ex) {
            throw new IllegalStateException("Publish mica-mqtt event failed", ex);
        }
    }

    private static String firstText(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
