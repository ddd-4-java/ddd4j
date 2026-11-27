/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.mq.mqttmica;

import io.ddd4j.kit.lang.IdKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.codec.properties.StringPair;
import org.dromara.mica.mqtt.codec.properties.UserProperty;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
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
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("mqtt-mica");

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
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(MqttPublishMessage message) {
        Map<String, String> headers = nativeHeaders(message);
        String messageId = headers.get(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : headers.get(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

    /**
     * \u63d0\u53d6\u539f\u751f MQTT \u53d1\u5e03\u6d88\u606f\u643a\u5e26\u7684\u7528\u6237\u5c5e\u6027\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @return \u6309\u5c5e\u6027\u540d\u7ec4\u7ec7\u7684\u6d88\u606f\u5934
     */
    private static Map<String, String> nativeHeaders(MqttPublishMessage message) {
        Map<String, String> headers = new HashMap<>();
        message.getProperties().getProperties().getProperties(MqttPropertyType.USER_PROPERTY.value())
                .forEach(property -> {
                    StringPair pair = ((UserProperty) property).value();
                    headers.put(pair.key, pair.value);
                });
        return headers;
    }

    /**
     * \u628a\u5e8f\u5217\u5316\u4e8b\u4ef6\u548c\u6d88\u606f\u5143\u6570\u636e\u53d1\u9001\u5230\u6307\u5b9a\u7684\u539f\u751f\u4ee3\u7406\u3002
     * @param client \u5f53\u524d\u4f7f\u7528\u7684\u539f\u751f\u4ee3\u7406\u6216 Redis \u5ba2\u6237\u7aef
     * @param topic \u8981\u53d1\u5e03\u3001\u8ba2\u9605\u6216\u53d6\u6d88\u8ba2\u9605\u7684\u4ee3\u7406\u4e3b\u9898
     * @param body \u8981\u53d1\u9001\u7684\u6d88\u606f\u8d1f\u8f7d\u5b57\u8282
     * @param qos MQTT \u6295\u9012\u4f7f\u7528\u7684\u670d\u52a1\u8d28\u91cf\u7b49\u7ea7
     * @param headers \u6d88\u606f\u968f\u534f\u8bae\u9644\u5e26\u7684\u5934\u5b57\u6bb5
     * @return \u5f53\u524d\u539f\u751f\u4ee3\u7406\u8fd4\u56de\u7684\u53d1\u9001\u7ed3\u679c
     */
    private static boolean publish(MqttClient client, String topic, byte[] body, MqttQoS qos,
                                   Map<String, String> headers) {
        if (headers.isEmpty()) {
            return client.publish(topic, body, qos);
        }
        // mica-mqtt 2.6.6 的编码器要求 User Properties 与其他属性一起聚合；
        // JSON 消息体是 UTF-8 文本，格式指示器 1 同时提供合法的聚合属性。
        return client.publish(topic, body, qos, builder -> builder.properties(properties -> {
            properties.setPayloadFormatIndicator(1);
            headers.forEach(properties::addUserProperty);
        }));
    }

    /**
     * \u7b49\u5f85\u539f\u751f MQTT \u5ba2\u6237\u7aef\u786e\u8ba4\u8fde\u63a5\u5b8c\u6210\uff0c\u8d85\u65f6\u540e\u7ec8\u6b62\u7b49\u5f85\u3002
     * @param connected \u7528\u6765\u5224\u65ad\u539f\u751f\u5ba2\u6237\u7aef\u662f\u5426\u5b8c\u6210\u8fde\u63a5\u7684\u56de\u8c03
     * @throws InterruptedException \u7b49\u5f85\u4ee3\u7406\u8fde\u63a5\u65f6\u7ebf\u7a0b\u88ab\u4e2d\u65ad
     */
    static void awaitConnected(BooleanSupplier connected) throws InterruptedException {
        // connectSync 只等待 TCP 连接；CONNACK 未到时 mica 会将订阅留到下次重连。
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!connected.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Mica MQTT client did not become ready for subscription");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            }
        }
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
     * 返回管理连接、生产者和消费者资源的生命周期对象。
     *
     * @return 当前消息适配器用于登记和关闭资源的生命周期对象
     */
    @Override
    public MQClientLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * 返回消息客户端当前记录的启动状态。
     *
     * @return 当前消息适配器记录的启动状态
     */
    @Override
    public MQStartupStatus startupStatus() {
        return startupStatus;
    }

    // ========================= 生产者 =========================

    /**
     * Broker 默认拼接符（各 broker 决定，建议子类覆写）。
     *
     * @return 实际返回<code>"/"</code>，供调用方取得当前值
     */
    @Override
    public String defaultConcat() {
        return "/";
    }

    // ========================= 消费者 =========================

    /**
     * mica-mqtt \u65e0\u539f\u751f broker-side tag selector\uff0c\u4ec5 topic \u901a\u914d \u2192 \u5f3a\u5236\u5e94\u7528\u5c42 {@link TagMatcher} \u8fc7\u6ee4\u3002
     *
     * @return \u5f53\u524d\u4ee3\u7406\u662f\u5426\u652f\u6301\u6309\u6807\u7b7e\u8fc7\u6ee4
     */
    @Override
    public boolean supportsBrokerTagFilter() {
        return false;
    }

    // ========================= 连接管理 =========================

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
                Map<String, String> headers = event.headerMap();
                boolean sent = publish(client, topic, body, qos(), headers);
                if (!sent) {
                    log.warn("Publish mica-mqtt [{}] failed (connection lost), reconnecting and retrying", topic);
                    client.reconnect();
                    sent = publish(client, topic, body, qos(), headers);
                }
                if (!sent) {
                    throw new IllegalStateException("Publish mica-mqtt event failed: " + topic);
                }
            } catch (Exception ex) {
                log.error("Publish mica-mqtt [{}]: {} failed!", topic, payload, ex);
                throw new IllegalStateException("Publish mica-mqtt event failed", ex);
            }
            log.info("Publish MQ [{}]: {}", topic, payload);
        };
    }

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
        awaitConnected(client::isConnected);
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
                Map<String, String> headers = nativeHeaders(message);
                String correlationId = headers.get(MQDeliveryHeaders.CORRELATION_ID);
                if (StrKit.isNotEmpty(correlationId)) {
                    event.setCorrelationId(correlationId);
                }
                String causationId = headers.get(MQDeliveryHeaders.CAUSATION_ID);
                if (StrKit.isNotEmpty(causationId)) {
                    event.setCausationId(causationId);
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
                MicaMqttAcknowledgment ack = new MicaMqttAcknowledgment(deliveryMessageId, arrivedTopic,
                        event.getCorrelationId());
                consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                log.error("Consume mica-mqtt [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
            }
        });
        lifecycle.register("mica-mqtt-subscription-" + subscribeTopic,
                () -> client.unSubscribe(subscribeTopic));
        return true;
    }

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
        MqttClient ownedClient = c;
        lifecycle.register("mica-mqtt-stop", () -> {
            if (!ownedClient.stop()) {
                throw new IllegalStateException("Stop mica MQTT client failed");
            }
        });
        lifecycle.register("mica-mqtt-disconnect", () -> {
            if (ownedClient.isConnected() && !ownedClient.disconnect()) {
                throw new IllegalStateException("Disconnect mica MQTT client failed");
            }
        });
        return c;
    }

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
    @Override
    public void close() {
        try {
            lifecycle.close();
        } finally {
            startupStatus.stopped();
        }
    }
}
