package io.ddd4j.mq.mqttmica.consumer;

import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.mqttmica.ack.MicaMqttMessageAcknowledgment;
import io.ddd4j.mq.mqttmica.spi.MicaMqttProperties;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQTagMatcher;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mica-mqtt 消费者端点注册器（编程式注册）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MicaMqttMQConsumerEndpointRegistrar {

    /** mica-mqtt 客户端实例 */
    private final MqttClient client;
    /** mica-mqtt 配置属性 */
    private final MicaMqttProperties properties;
    /** 消息 ID 生成器 */
    private final AtomicLong idGen = new AtomicLong(1);

    /**
     * 构造 mica-mqtt 消费者端点注册器。
     *
     * @param client     mica-mqtt 客户端
     * @param properties mica-mqtt 配置属性
     */
    public MicaMqttMQConsumerEndpointRegistrar(MqttClient client, MicaMqttProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 注册 MQ 监听器到 mica-mqtt 消费者端点。
     *
     * @param definition 监听器定义
     * @param handler    消费处理器
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        String topic = Objects.isNull(definition.getTopic()) ? "ddd4j/default/topic" : definition.getTopic();
        String tag = MQTagMatcher.findIncludes(definition.getTags()).stream().findFirst().orElse(null);
        String subscribeTopic = (Objects.isNull(tag)) ? topic : topic + "/#";
        if (Objects.nonNull(definition.getNamespace()) && !io.ddd4j.kit.lang.StrKit.isBlank(definition.getNamespace())) {
            subscribeTopic = definition.getNamespace() + "/" + subscribeTopic;
        }
        // mica AIO client API: subscribe(topic, qos, messageHandler)
        client.subscribe(subscribeTopic, properties.mqttQoS(), (ctx, topic1, message, payload) -> {
            try {
                handleMessage(topic1, payload, definition, handler);
            } catch (Exception ignore) {
            }
        });
    }

    private void handleMessage(String topic, byte[] payload, MQListenerDefinition def, MQConsumerHandler handler) {
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
        long messageId = idGen.getAndIncrement();
        headers.put(MicaMqttMessageAcknowledgment.HEADER_MICA_TOPIC, topic);
        headers.put(MicaMqttMessageAcknowledgment.HEADER_MICA_MESSAGE_ID, messageId);
        MQMessage<String> mq = MQMessage.of(
                new String(payload, StandardCharsets.UTF_8), headers, null, null, payload);
        try {
            handler.handle(mq, new MicaMqttMessageAcknowledgment(messageId, topic, null));
        } catch (Exception ignore) {
            // mica-mqtt does not expose a native negative acknowledgment path.
        }
    }
}
