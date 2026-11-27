package io.ddd4j.mq.mqttmica;

import io.ddd4j.kit.lang.IdKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * mica-mqtt AIO 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>mica-mqtt 协议层自动处理 PUBACK；无原生 broker-side tag filter，应用层 {@link TagMatcher} 过滤保留。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : MicaMqttMQClient ###")
public class MicaMqttMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final MicaMqttProperties properties;
    /** \u6301\u6709\u5f53\u524d\u5df2\u521b\u5efa\u4e14\u53ef\u7528\u7684\u539f\u751f\u6d88\u606f\u5ba2\u6237\u7aef\u3002 */
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();

    /**
     * 双构造 1：注入已初始化的原生 mica-mqtt 客户端（用于 runtime 集成自动注入）。
     *
     * @param client 原生 mica-mqtt 客户端
     */
    public MicaMqttMQClient(MqttClient client) {
        this.properties = new MicaMqttProperties();
        if (Objects.nonNull(client)) {
            this.clientRef.set(client);
        }
    }

    /**
     * 双构造 2：传入配置，{@link #client()} 时 lazy 构造原生 mica-mqtt 客户端。
     *
     * @param properties mica-mqtt 配置
     */
    public MicaMqttMQClient(MicaMqttProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"mqtt-mica"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "mqtt-mica";
    }

    /**
     * Broker 默认拼接符（各 broker 决定，建议子类覆写）。
     *
     * @return 实际返回<code>"/"</code>，供调用方取得当前值
     */
    @Override
    public String defaultConcat() {
        return "/";
    }

    /**
     * mica-mqtt \u65e0\u539f\u751f broker-side tag selector\uff0c\u4ec5 topic \u901a\u914d \u2192 \u5f3a\u5236\u5e94\u7528\u5c42 {@link TagMatcher} \u8fc7\u6ee4\u3002
     *
     * @return \u5f53\u524d\u4ee3\u7406\u662f\u5426\u652f\u6301\u6309\u6807\u7b7e\u8fc7\u6ee4
     */
    @Override
    public boolean supportsBrokerTagFilter() {
        return false;
    }

    // ========================= 生产者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>MQTT 实现调用 <code>client()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        MqttClient client = client();
        return event -> {
            String payload = serialization().serialize(event);
            String topic = resolveTopic(event, mqProperties);
            try {
                byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                client.publish(topic, body, qos(), builder -> builder.properties(mqttProperties -> {
                    if (StrKit.isNotEmpty(event.getMsgId())) {
                        mqttProperties.addUserProperty(MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
                    }
                }));
            } catch (Exception ex) {
                log.error("Publish mica-mqtt [{}]: {} failed!", topic, payload, ex);
                throw new IllegalStateException("Publish mica-mqtt event failed", ex);
            }
            log.info("Publish MQ [{}]: {}", topic, payload);
        };
    }

    // ========================= 消费者 =========================

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>MQTT 实现调用 <code>client()</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        MqttClient client = client();
        // mica subscribe 通配：保留原生 subscribe `topic/#` 行为（首个 include tag 拼接到末尾 /#）
        String topic = resolveTopic(listener, mqProperties);
        String includeTag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        String subscribeTopic = Objects.isNull(includeTag) ? topic : topic + "/#";
        client.subscribe(subscribeTopic, qos(), (ctx, arrivedTopic, message, payload) -> {
            try {
                String payloadStr = new String(payload, StandardCharsets.UTF_8);
                MQEvent event = serialization().deserialize(payloadStr, listener.payloadType());
                if (Objects.isNull(event)) {
                    log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(defaultConcat()));
                    return;
                }
                String messageId = messageId(message);
                if (StrKit.isNotEmpty(messageId)) {
                    event.setMsgId(messageId);
                }
                // 应用层 tag 过滤（tag 取 topic 末段）。
                String tag = null;
                int lastSlash = arrivedTopic.lastIndexOf('/');
                if (lastSlash >= 0) {
                    tag = arrivedTopic.substring(lastSlash + 1);
                }
                if (!TagMatcher.match(tag, listener.getTags())) {
                    return;
                }
                long deliveryMessageId = IdKit.getSnowflakeNextId();
                MicaMqttAcknowledgment ack = new MicaMqttAcknowledgment(deliveryMessageId, arrivedTopic, null);
                consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                log.error("Consume mica-mqtt [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
            }
        });
        return true;
    }

    // ========================= 连接管理 =========================

    /**
     * QoS\uff1a\u6ce8\u5165\u539f\u751f\u5ba2\u6237\u7aef\u65f6\u53d6\u9ed8\u8ba4 QoS = QOS1\uff0c\u5426\u5219\u8bfb properties\u3002
     *
     * @return MQTT \u53d1\u5e03\u6216\u8ba2\u9605\u4f7f\u7528\u7684\u670d\u52a1\u8d28\u91cf\u7ea7\u522b
     */
    private MqttQoS qos() {
        return properties.mqttQoS();
    }

    /**
     * \u53d6\u5f97\u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef\uff0c\u7f3a\u5c11\u5b9e\u4f8b\u65f6\u5efa\u7acb\u8fde\u63a5\u3002
     * @return \u4f9b\u53d1\u5e03\u6216\u6d88\u8d39\u64cd\u4f5c\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef
     */
    private synchronized MqttClient client() {
        MqttClient c = clientRef.get();
        if (Objects.nonNull(c)) {
            return c;
        }
        c = properties.client();
        clientRef.set(c);
        return c;
    }

    /**
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(MqttPublishMessage message) {
        String messageId = message.getProperties().getUserPropertiesMap().get(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : message.getProperties().getUserPropertiesMap().get(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }
}
