package io.ddd4j.mq.mqttmica.publisher;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.mqttmica.spi.MicaMqttProperties;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.serialization.EventSerialization;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * mica-mqtt AIO 事件发布器（纯 Java）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MicaMqttEventPublisher implements EventPublisher {

    /** mica-mqtt 客户端实例 */
    private final MqttClient client;
    /** mica-mqtt 配置属性 */
    private final MicaMqttProperties properties;
    /** MQ 全局配置 */
    private final MQProperties mqProperties;
    /** 事件序列化器 */
    private final EventSerialization serialization;

    public MicaMqttEventPublisher(MqttClient client, MicaMqttProperties properties,
                                    MQProperties mqProperties, EventSerialization serialization) {
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
            String ns = destination.getNamespace();
            String tag = destination.getTag();
            String physical = (StrKit.hasText(ns) ? ns + "/" : "") + topic
                    + (StrKit.hasText(tag) ? "/" + tag : "");
            client.publish(physical,
                    serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8),
                    properties.mqttQoS());
        } catch (Exception ex) {
            throw new IllegalStateException("Publish mica-mqtt event failed", ex);
        }
    }
}
