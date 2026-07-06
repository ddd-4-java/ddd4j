package io.ddd4j.mq.mqtt.publisher;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.mqtt.spi.MqttMQProperties;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.event.MQEventSerialization;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Eclipse Paho MQTT 事件发布器（纯 Java）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MqttMQEventPublisher implements MQEventPublisher {

    private final MqttClient client;
    private final MqttMQProperties properties;
    private final MQProperties mqProperties;
    private final MQEventSerialization serialization;

    public MqttMQEventPublisher(MqttClient client, MqttMQProperties properties,
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
            String topic = StrKit.hasText(destination.getTopic())
                    ? destination.getTopic()
                    : (StrKit.hasText(event.getTopic()) ? event.getTopic() : "ddd4j/default/topic");
            // MQTT topic 层级用 / 替代 .（MQTT 协议约定）；namespace 作前缀
            String ns = destination.getNamespace();
            String tag = destination.getTag();
            String physical = (StrKit.hasText(ns) ? ns + "/" : "") + topic
                    + (StrKit.hasText(tag) ? "/" + tag : "");
            MqttMessage msg = new MqttMessage(serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8));
            msg.setQos(properties.getQos());
            if (Objects.nonNull(event.getMsgId())) {
                msg.setId(Integer.parseInt(event.getMsgId().hashCode() + ""));
            }
            client.publish(physical, msg);
        } catch (Exception ex) {
            throw new IllegalStateException("Publish MQTT event failed", ex);
        }
    }
}
