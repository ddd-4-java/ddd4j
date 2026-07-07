package io.ddd4j.mq.mqttmica;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.util.TagMatcher;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * mica-mqtt AIO 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer} —— 建 mica AIO 客户端连接，返回 {@link Consumer<MQEvent>}，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— 在同一连接上订阅 topic，收到消息后做 tag 过滤、
 *       反序列化 → 构建 {@link MicaMqttAcknowledgment} → 调 {@link #consume} 统一消费</li>
 * </ul>
 *
 * <p>mica-mqtt 协议层自动处理 PUBACK；{@link MicaMqttAcknowledgment} 仅作为"已处理"标记位。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class MicaMqttClient implements MQClient {

    private final MicaMqttProperties properties;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public MicaMqttClient(MicaMqttProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "mqtt-mica";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        MqttClient client = connection();
        return event -> publish(client, event);
    }

    private void publish(MqttClient client, MQEvent event) {
        try {
            String physical = resolvePhysical(event.getNamespace(), event.getTopic(), event.getTag());
            byte[] body = serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8);
            client.publish(physical, body, properties.mqttQoS());
            logger().info("Publish mica-mqtt [{}]: {}", physical, serialization().serialize(event));
        } catch (Exception ex) {
            throw new IllegalStateException("Publish mica-mqtt event failed", ex);
        }
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        MqttClient client = connection();
        String subscribeTopic = resolveSubscribeTopic(listener);
        client.subscribe(subscribeTopic, properties.mqttQoS(), (ctx, topic, message, payload) -> {
            try {
                handleMessage(topic, payload, listener);
            } catch (Exception ex) {
                logger().error("Consume mica-mqtt [{}] failed", listener.namespaceTopicTags(), ex);
            }
        });
        return true;
    }

    private void handleMessage(String topic, byte[] payload, MQListener listener) {
        try {
            String tag = null;
            int lastSlash = topic.lastIndexOf('/');
            if (lastSlash >= 0) {
                tag = topic.substring(lastSlash + 1);
            }
            if (!TagMatcher.match(tag, listener.getTags())) {
                return;
            }
            String payloadStr = new String(payload, StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payloadStr, listener.payloadType());
            long messageId = idGen.getAndIncrement();
            Acknowledgment ack = new MicaMqttAcknowledgment(messageId, topic, null);
            consume(listener, event, ack);
            if (!ack.isAcknowledged()) {
                ack.ackSingle();
            }
        } catch (Throwable ex) {
            logger().error("Consume mica-mqtt [{}] failed", listener.namespaceTopicTags(), ex);
        }
    }

    // ========================= 工具 =========================

    /**
     * 拼接发布物理地址 {@code [namespace/]topic[/tag]}。MQTT 协议约定层级用 {@code /} 替代 {@code .}。
     */
    private static String resolvePhysical(String namespace, String topic, String tag) {
        String base = io.ddd4j.kit.lang.StrKit.hasText(topic) ? topic : "ddd4j/default/topic";
        String ns = io.ddd4j.kit.lang.StrKit.hasText(namespace) ? namespace + "/" : "";
        String t = io.ddd4j.kit.lang.StrKit.hasText(tag) ? "/" + tag : "";
        return ns + base + t;
    }

    /**
     * 解析监听器定义对应的订阅主题 {@code [namespace/]topic[/#]}。有 tag 时通配订阅其下所有子级。
     */
    private static String resolveSubscribeTopic(MQListener listener) {
        String topic = Objects.isNull(listener.getTopic()) ? "ddd4j/default/topic" : listener.getTopic();
        String tag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        String subscribeTopic = Objects.isNull(tag) ? topic : topic + "/#";
        if (Objects.nonNull(listener.getNamespace()) && !io.ddd4j.kit.lang.StrKit.isBlank(listener.getNamespace())) {
            subscribeTopic = listener.getNamespace() + "/" + subscribeTopic;
        }
        return subscribeTopic;
    }

    private synchronized MqttClient connection() {
        MqttClient c = clientRef.get();
        if (Objects.isNull(c)) {
            c = properties.client();
            clientRef.set(c);
        }
        return c;
    }
}
