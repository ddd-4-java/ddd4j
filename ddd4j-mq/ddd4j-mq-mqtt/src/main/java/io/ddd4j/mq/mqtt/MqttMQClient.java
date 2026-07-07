package io.ddd4j.mq.mqtt;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Eclipse Paho MQTT 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>MQTT 无原生 broker-side tag filter（仅 topic 通配），应用层 tag 过滤保留。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : MqttMQClient ###")
public class MqttMQClient implements MQClient {

    private final MqttMQProperties properties;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();

    /**
     * 构造 1：注入已初始化的原生 Paho 客户端（用于 runtime 集成自动注入）。
     *
     * @param client 原生 MQTT 客户端
     */
    public MqttMQClient(MqttClient client) {
        this.properties = null;
        if (Objects.nonNull(client)) {
            this.clientRef.set(client);
        }
    }

    /**
     * 构造 2：传入配置，{@link #getMqttClient()} 时 lazy 构造原生客户端。
     *
     * @param properties MQTT 配置
     */
    public MqttMQClient(MqttMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "mqtt";
    }

    @Override
    public String defaultConcat() {
        return "/";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        MqttClient client = getMqttClient();
        return event -> {
            try {
                String physical = resolveTopic(event, mqProperties);
                byte[] body = serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8);
                MqttMessage msg = new MqttMessage(body);
                msg.setQos(qos());
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
        };
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        org.eclipse.paho.client.mqttv3.MqttClient client = getMqttClient();
        // MQTT subscribe 通配：保留原生 subscribe `topic/#` 行为（首个 include tag 拼接到末尾 /#）
        String physical = resolveTopic(listener, mqProperties);
        String includeTag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        String subscribeTopic = Objects.isNull(includeTag) ? physical : physical + "/#";
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                logger().warn("MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String arrivedTopic, MqttMessage message) {
                try {
                    String tag = null;
                    int lastSlash = arrivedTopic.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        tag = arrivedTopic.substring(lastSlash + 1);
                    }
                    if (!TagMatcher.match(tag, listener.getTags())) {
                        return;
                    }
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    MQEvent event = serialization().deserialize(payload, listener.payloadType());
                    Acknowledgment ack = new MqttAcknowledgment(message, arrivedTopic);
                    consume(listener, event, ack);
                    if (!ack.isAcknowledged()) {
                        ack.ackSingle();
                    }
                } catch (Throwable ex) {
                    logger().error("Consume MQTT [{}] failed", listener.getRouteExpression(defaultConcat()), ex);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        client.subscribe(subscribeTopic, qos());
        return true;
    }

    // ========================= 连接管理 =========================

    /**
     * QoS：注入原生客户端（无 properties）时取默认值 1。
     */
    private int qos() {
        return Objects.isNull(properties) ? 1 : properties.getQos();
    }

    private synchronized MqttClient getMqttClient() {
        MqttClient c = clientRef.get();
        if (Objects.nonNull(c)) {
            return c;
        }
        try {
            MqttClient nc = new MqttClient(properties.getServerUri(), properties.newClientId());
            nc.connect(properties.connectOptions());
            clientRef.set(nc);
            c = nc;
        } catch (Exception ex) {
            throw new IllegalStateException("Open MQTT connection failed", ex);
        }
        return c;
    }
}