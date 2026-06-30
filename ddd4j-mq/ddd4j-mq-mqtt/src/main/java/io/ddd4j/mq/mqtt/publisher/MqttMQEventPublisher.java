package io.ddd4j.mq.mqtt.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.mqtt.spi.MqttMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Eclipse Paho MQTT event publisher (pure Java).
 */
public class MqttMQEventPublisher implements MQEventPublisher {

    private final MqttClient client;
    private final MqttMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;

    public MqttMQEventPublisher(MqttClient client, MqttMQProperties properties,
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
            // MQTT topic 层级用 / 替代 .（MQTT 协议约定）；namespace 作前缀
            String ns = destination.getNamespace();
            String tag = destination.getTag();
            String physical = (java.util.Objects.isNull(ns) || io.ddd4j.kit.lang.StrKit.isBlank(ns) ? "" : ns + "/") + topic
                    + (java.util.Objects.isNull(tag) || io.ddd4j.kit.lang.StrKit.isBlank(tag) ? "" : "/" + tag);
            MqttMessage msg = new MqttMessage(serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8));
            msg.setQos(properties.getQos());
            if (java.util.Objects.nonNull(event.getMsgId())) {
                msg.setId(Integer.parseInt(event.getMsgId().hashCode() + ""));
            }
            client.publish(physical, msg);
        } catch (Exception ex) {
            throw new IllegalStateException("Publish MQTT event failed", ex);
        }
    }

    private static String firstText(String... values) {
        if (java.util.Objects.isNull(values)) {
            return null;
        }
        for (String v : values) {
            if (java.util.Objects.nonNull(v) && !io.ddd4j.kit.lang.StrKit.isBlank(v)) {
                return v;
            }
        }
        return null;
    }
}
