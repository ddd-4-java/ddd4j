package io.ddd4j.mq.mqttmica;

import io.ddd4j.kit.lang.IdKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * mica-mqtt AIO 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>mica-mqtt 协议层自动处理 PUBACK；无原生 broker-side tag filter，应用层过滤保留。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : MicaMqttMQClient ###")
public class MicaMqttMQClient implements MQClient {

    private final MicaMqttProperties properties;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();

    /**
     * 构造 1：注入已初始化的原生 mica-mqtt 客户端（用于 runtime 集成自动注入）。
     *
     * @param client 原生 mica-mqtt 客户端
     */
    public MicaMqttMQClient(MqttClient client) {
        this.properties = null;
        if (Objects.nonNull(client)) {
            this.clientRef.set(client);
        }
    }

    /**
     * 构造 2：传入配置，{@link #client()} 时 lazy 构造原生 mica-mqtt 客户端。
     *
     * @param properties mica-mqtt 配置
     */
    public MicaMqttMQClient(MicaMqttProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "mqtt-mica";
    }

    @Override
    public String defaultConcat() {
        return "/";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        MqttClient client = client();
        return event -> {
            try {
                String physical = resolveTopic(event, mqProperties);
                byte[] body = serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8);
                client.publish(physical, body, qos());
                logger().info("Publish mica-mqtt [{}]: {}", physical, serialization().serialize(event));
            } catch (Exception ex) {
                throw new IllegalStateException("Publish mica-mqtt event failed", ex);
            }
        };
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        MqttClient client = client();
        // mica subscribe 通配：保留原生 subscribe `topic/#` 行为（首个 include tag 拼接到末尾 /#）
        String physical = resolveTopic(listener, mqProperties);
        String includeTag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        String subscribeTopic = Objects.isNull(includeTag) ? physical : physical + "/#";
        client.subscribe(subscribeTopic, qos(), (ctx, topic, message, payload) -> {
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
                long messageId = IdKit.getSnowflakeNextId();
                Acknowledgment ack = new MicaMqttAcknowledgment(messageId, topic, null);
                consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                logger().error("Consume mica-mqtt [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
            }
        });
        return true;
    }

    // ========================= 连接管理 =========================

    /**
     * QoS：注入原生客户端（无 properties）时取默认 QoS = QOS1。
     */
    private MqttQoS qos() {
        return Objects.isNull(properties) ? MqttQoS.QOS1 : properties.mqttQoS();
    }

    private synchronized MqttClient client() {
        MqttClient c = clientRef.get();
        if (Objects.nonNull(c)) {
            return c;
        }
        c = properties.client();
        clientRef.set(c);
        return c;
    }
}