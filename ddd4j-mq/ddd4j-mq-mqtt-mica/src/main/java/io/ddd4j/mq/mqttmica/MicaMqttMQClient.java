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

    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
    private final MicaMqttProperties properties;
    /**
     * 持有当前已创建且可用的原生消息客户端。
     */
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
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
     * 优先从原生消息的标准头读取消息标识，缺少时回退到兼容头。
     *
     * @param message 当前接收的原生代理消息
     * @return 用于后续消费确认的业务消息标识，头均缺失时为空
     */
    static String messageId(MqttPublishMessage message) {
        Map<String, String> headers = nativeHeaders(message);
        String messageId = headers.get(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : headers.get(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

    /**
     * 提取原生 MQTT 发布消息携带的用户属性。
     *
     * @param message 当前接收的原生代理消息
     * @return 按属性名组织的消息头
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
     * 把序列化事件和消息元数据发送到指定的原生代理。
     *
     * @param client  当前使用的原生代理或 Redis 客户端
     * @param topic   要发布、订阅或取消订阅的代理主题
     * @param body    要发送的消息负载字节
     * @param qos     MQTT 投递使用的服务质量等级
     * @param headers 消息随协议附带的头字段
     * @return 当前原生代理返回的发送结果
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
     * 等待原生 MQTT 客户端确认连接完成，超时后终止等待。
     *
     * @param connected 用来判断原生客户端是否完成连接的回调
     * @throws InterruptedException 等待代理连接时线程被中断
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
     * mica-mqtt 无原生 broker-side tag selector，仅 topic 通配 → 强制应用层 {@link TagMatcher} 过滤。
     *
     * @return 当前代理是否支持按标签过滤
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
     * @param listener     监听器定义
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
     * QoS：注入原生客户端时取默认 QoS = QOS1，否则读 properties。
     *
     * @return MQTT 发布或订阅使用的服务质量级别
     */
    private MqttQoS qos() {
        return properties.mqttQoS();
    }

    /**
     * 取得当前适配器使用的原生连接或客户端，缺少实例时建立连接。
     *
     * @return 供发布或消费操作使用的原生连接或客户端
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

    /**
     * 释放消息客户端拥有的运行资源，并在退出时登记组件已停止。
     */
    @Override
    public void close() {
        try {
            lifecycle.close();
        } finally {
            startupStatus.stopped();
        }
    }
}
