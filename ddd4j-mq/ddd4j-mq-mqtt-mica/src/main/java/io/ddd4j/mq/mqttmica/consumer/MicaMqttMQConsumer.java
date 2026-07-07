package io.ddd4j.mq.mqttmica.consumer;

import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.mqttmica.ack.MicaMqttAcknowledgment;
import io.ddd4j.mq.mqttmica.spi.MicaMqttProperties;
import io.ddd4j.mq.util.TagMatcher;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mica-mqtt 消费者实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQEventConsumer}，在 {@link #subscribe(MQListener, MQEventCallback)} 中建立 mica-mqtt 订阅，
 * 收到消息后做 tag 过滤、提取 payload 字符串、构建 {@link MicaMqttAcknowledgment}，
 * 通过 {@link MQEventCallback} 交给 core 统一处理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class MicaMqttMQConsumer implements MQEventConsumer {

    /** mica-mqtt 客户端实例 */
    private final MqttClient client;
    /** mica-mqtt 配置属性 */
    private final MicaMqttProperties properties;
    /** 消息 ID 生成器 */
    private final AtomicLong idGen = new AtomicLong(1);

    /**
     * 构造 mica-mqtt 消费者。
     *
     * @param client     mica-mqtt 客户端
     * @param properties mica-mqtt 配置属性
     */
    public MicaMqttMQConsumer(MqttClient client, MicaMqttProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void subscribe(MQListener listener, MQEventCallback onEvent) {
        String topic = Objects.isNull(listener.getTopic()) ? "ddd4j/default/topic" : listener.getTopic();
        String tag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        String subscribeTopic = (Objects.isNull(tag)) ? topic : topic + "/#";
        if (Objects.nonNull(listener.getNamespace()) && !io.ddd4j.kit.lang.StrKit.isBlank(listener.getNamespace())) {
            subscribeTopic = listener.getNamespace() + "/" + subscribeTopic;
        }
        // mica AIO client API: subscribe(topic, qos, messageHandler)
        client.subscribe(subscribeTopic, properties.mqttQoS(), (ctx, topic1, message, payload) -> {
            try {
                handleMessage(topic1, payload, listener, onEvent);
            } catch (Exception ignore) {
            }
        });
    }

    private void handleMessage(String topic, byte[] payload, MQListener listener, MQEventCallback onEvent) {
        String tag = null;
        int lastSlash = topic.lastIndexOf('/');
        if (lastSlash >= 0) {
            tag = topic.substring(lastSlash + 1);
        }
        if (!TagMatcher.match(tag, listener.getTags())) {
            return;
        }
        long messageId = idGen.getAndIncrement();
        String payloadStr = new String(payload, StandardCharsets.UTF_8);
        Acknowledgment ack = new MicaMqttAcknowledgment(messageId, topic, null);
        try {
            onEvent.onEvent(payloadStr, Long.toString(messageId), null, tag, ack);
        } catch (Throwable ignore) {
            // mica-mqtt does not expose a native negative acknowledgment path.
        }
    }
}
