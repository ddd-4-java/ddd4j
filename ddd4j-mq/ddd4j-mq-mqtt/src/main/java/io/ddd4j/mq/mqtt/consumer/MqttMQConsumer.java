package io.ddd4j.mq.mqtt.consumer;

import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.mqtt.ack.MqttAcknowledgment;
import io.ddd4j.mq.mqtt.spi.MqttMQProperties;
import io.ddd4j.mq.util.TagMatcher;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Eclipse Paho MQTT 消费者实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQEventConsumer}，在 {@link #subscribe(MQListener, MQEventCallback)} 中
 * 解析订阅主题（{@code namespace/topic/#}），注册 Paho 回调，收到消息后做 tag 过滤、
 * 提取 payload 字符串，构建 {@link MqttAcknowledgment}，通过 {@link MQEventCallback} 交给 core 处理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class MqttMQConsumer implements MQEventConsumer {

    private final MqttClient client;
    private final MqttMQProperties properties;

    public MqttMQConsumer(MqttClient client, MqttMQProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void subscribe(MQListener listener, MQEventCallback onEvent) {
        Objects.requireNonNull(listener, "listener");
        try {
            String topic = Objects.isNull(listener.getTopic()) ? "ddd4j/default/topic" : listener.getTopic();
            String tag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
            // MQTT 通配符：监听 tag=foo 的，使用 foo/#；仅监听主 topic，使用 topic
            String subscribeTopic = Objects.isNull(tag) ? topic : topic + "/#";
            if (Objects.nonNull(listener.getNamespace()) && !io.ddd4j.kit.lang.StrKit.isBlank(listener.getNamespace())) {
                subscribeTopic = listener.getNamespace() + "/" + subscribeTopic;
            }
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String arrivedTopic, MqttMessage message) {
                    handleMessage(arrivedTopic, message, listener, onEvent);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            client.subscribe(subscribeTopic, properties.getQos());
        } catch (Exception ex) {
            throw new IllegalStateException("Subscribe MQTT consumer failed: " + listener.namespaceTopicTags(), ex);
        }
    }

    private void handleMessage(String topic, MqttMessage message, MQListener listener, MQEventCallback onEvent) {
        String tag = null;
        int lastSlash = topic.lastIndexOf('/');
        if (lastSlash >= 0) {
            tag = topic.substring(lastSlash + 1);
        }
        if (!TagMatcher.match(tag, listener.getTags())) {
            return;
        }
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        String messageId = Integer.toString(message.getId());
        Acknowledgment ack = new MqttAcknowledgment(message, topic);
        try {
            onEvent.onEvent(payload, messageId, null, tag, ack);
        } catch (Throwable ex) {
            // MQTT 没有原生 nack 通道
        }
    }
}
