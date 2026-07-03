package io.ddd4j.mq.mqttmica.publisher;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.mqttmica.spi.MicaMqttProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * mica-mqtt AIO 事件发布器（纯 Java）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MicaMqttMQEventPublisher implements MQEventPublisher {

    /** mica-mqtt 客户端实例 */
    private final MqttClient client;
    /** mica-mqtt 配置属性 */
    private final MicaMqttProperties properties;
    /** MQ 全局配置 */
    private final Ddd4jMQProperties mqProperties;
    /** 事件序列化器 */
    private final MQEventSerialization serialization;

    public MicaMqttMQEventPublisher(MqttClient client, MicaMqttProperties properties,
                                    Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    private static String firstText(String... values) {
        if (Objects.isNull(values)) {
            return null;
        }
        for (String v : values) {
            if (Objects.nonNull(v) && !io.ddd4j.kit.lang.StrKit.isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        try {
            String topic = firstText(destination.getTopic(), event.getTopic(), "ddd4j/default/topic");
            String ns = destination.getNamespace();
            String tag = destination.getTag();
            String physical = (Objects.isNull(ns) || io.ddd4j.kit.lang.StrKit.isBlank(ns) ? "" : ns + "/") + topic
                    + (Objects.isNull(tag) || io.ddd4j.kit.lang.StrKit.isBlank(tag) ? "" : "/" + tag);
            client.publish(physical,
                    serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8),
                    properties.mqttQoS());
        } catch (Exception ex) {
            throw new IllegalStateException("Publish mica-mqtt event failed", ex);
        }
    }
}
