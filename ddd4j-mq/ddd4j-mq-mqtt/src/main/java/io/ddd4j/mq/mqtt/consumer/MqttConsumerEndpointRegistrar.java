package io.ddd4j.mq.mqtt.consumer;

import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.mqtt.ack.MqttMessageAcknowledgment;
import io.ddd4j.mq.mqtt.spi.MqttMQProperties;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQTagMatcher;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Eclipse Paho MQTT 消费者端点注册器（编程式注册）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MqttConsumerEndpointRegistrar {

    private final MqttClient client;
    private final MqttMQProperties properties;

    public MqttConsumerEndpointRegistrar(MqttClient client, MqttMQProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        try {
            String topic = Objects.isNull(definition.getTopic()) ? "ddd4j/default/topic" : definition.getTopic();
            String tag = io.ddd4j.mq.registry.MQTagMatcher.findIncludes(definition.getTags())
                    .stream().findFirst().orElse(null);
            // MQTT 通配符：监听 tag=foo 的，使用 foo/#；仅监听主 topic，使用 topic
            String subscribeTopic = (Objects.isNull(tag)) ? topic : topic + "/#";
            if (Objects.nonNull(definition.getNamespace()) && !io.ddd4j.kit.lang.StrKit.isBlank(definition.getNamespace())) {
                subscribeTopic = definition.getNamespace() + "/" + subscribeTopic;
            }
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String arrivedTopic, MqttMessage message) {
                    handleMessage(arrivedTopic, message, definition, handler);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            client.subscribe(subscribeTopic, properties.getQos());
        } catch (Exception ex) {
            throw new IllegalStateException("Register MQTT consumer failed", ex);
        }
    }

    private void handleMessage(String topic, MqttMessage message, MQListenerDefinition def, MQConsumerHandler handler) {
        String tag = null;
        int lastSlash = topic.lastIndexOf('/');
        if (lastSlash >= 0) {
            tag = topic.substring(lastSlash + 1);
        }
        if (!MQTagMatcher.match(tag, def.getTags())) {
            return;
        }
        Map<String, Object> headers = new HashMap<>();
        headers.put(MQMessages.HEADER_DESTINATION_TOPIC, def.getTopic());
        if (Objects.nonNull(tag)) {
            headers.put(MQMessages.HEADER_DESTINATION_TAG, tag);
        }
        headers.put(MqttMessageAcknowledgment.HEADER_MQTT_MESSAGE, message);
        headers.put(MqttMessageAcknowledgment.HEADER_MQTT_TOPIC, topic);
        MQMessage<String> mq = MQMessage.of(
                new String(message.getPayload(), StandardCharsets.UTF_8),
                headers,
                Integer.toString(message.getId()),
                null,
                message);
        MqttMessageAcknowledgment ack = new MqttMessageAcknowledgment(message, topic);
        try {
            handler.handle(mq, ack);
        } catch (Exception ex) {
            // MQTT 没有原生 nack 通道
        }
    }
}
