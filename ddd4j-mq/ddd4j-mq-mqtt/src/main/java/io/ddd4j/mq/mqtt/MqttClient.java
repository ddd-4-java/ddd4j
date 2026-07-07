package io.ddd4j.mq.mqtt;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.util.TagMatcher;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Eclipse Paho MQTT 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer} —— 建 Paho {@link MqttClient} 连接，返回 {@link Consumer<MQEvent>}，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— 在同一连接上订阅 topic，收到消息后做 tag 过滤、
 *       反序列化 → 构建 {@link MqttAcknowledgment} → 调 {@link #consume} 统一消费</li>
 * </ul>
 *
 * <p>MQTT 没有原生 broker-side ack 通道，{@link MqttAcknowledgment} 仅作为"已处理"标记位；
 * 业务层如需 DLQ 策略，可订阅 DLQ topic 重新发布。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class MqttClient implements MQClient {

    private final MqttProperties properties;
    private final AtomicReference<org.eclipse.paho.client.mqttv3.MqttClient> clientRef = new AtomicReference<>();

    public MqttClient(MqttProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "mqtt";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        org.eclipse.paho.client.mqttv3.MqttClient client = connection();
        return event -> publish(client, event);
    }

    private void publish(org.eclipse.paho.client.mqttv3.MqttClient client, MQEvent event) {
        try {
            String physical = resolvePhysical(event.getNamespace(), event.getTopic(), event.getTag());
            byte[] body = serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8);
            MqttMessage msg = new MqttMessage(body);
            msg.setQos(properties.getQos());
            if (Objects.nonNull(event.getMsgId())) {
                try {
                    msg.setId(Integer.parseInt(event.getMsgId().hashCode() + ""));
                } catch (Exception ignore) {
                }
            }
            client.publish(physical, msg);
            logger().info("Publish MQTT [{}]: {}", physical, serialization().serialize(event));
        } catch (Exception ex) {
            throw new IllegalStateException("Publish MQTT event failed", ex);
        }
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        org.eclipse.paho.client.mqttv3.MqttClient client = connection();
        String subscribeTopic = resolveSubscribeTopic(listener);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                logger().warn("MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String arrivedTopic, MqttMessage message) {
                handleMessage(arrivedTopic, message, listener);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        client.subscribe(subscribeTopic, properties.getQos());
        return true;
    }

    private void handleMessage(String topic, MqttMessage message, MQListener listener) {
        try {
            String tag = null;
            int lastSlash = topic.lastIndexOf('/');
            if (lastSlash >= 0) {
                tag = topic.substring(lastSlash + 1);
            }
            if (!TagMatcher.match(tag, listener.getTags())) {
                return;
            }
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            Acknowledgment ack = new MqttAcknowledgment(message, topic);
            consume(listener, event, ack);
            if (!ack.isAcknowledged()) {
                ack.ackSingle();
            }
        } catch (Throwable ex) {
            logger().error("Consume MQTT [{}] failed", listener.namespaceTopicTags(), ex);
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

    private synchronized org.eclipse.paho.client.mqttv3.MqttClient connection() {
        org.eclipse.paho.client.mqttv3.MqttClient c = clientRef.get();
        if (Objects.isNull(c)) {
            try {
                org.eclipse.paho.client.mqttv3.MqttClient nc = new org.eclipse.paho.client.mqttv3.MqttClient(
                        properties.getServerUri(), properties.newClientId());
                nc.connect(properties.connectOptions());
                clientRef.set(nc);
                c = nc;
            } catch (Exception ex) {
                throw new IllegalStateException("Open MQTT connection failed", ex);
            }
        }
        return c;
    }
}
