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
package io.ddd4j.mq.mqtt;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
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
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>MQTT 无原生 broker-side tag filter（仅 topic 通配），应用层 {@link TagMatcher} tag 过滤保留。
 *
 * <p>注意：Paho 原生类名 {@code MqttClient} 与 ddd4j 的 {@link MQClient} 概念相近但不同，
 * 本类统一用全限定名 {@link org.eclipse.paho.client.mqttv3.MqttClient} 避免歧义。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : MqttMQClient ###")
public class MqttMQClient implements MQClient {

    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
    private final MqttMQProperties properties;
    /**
     * 持有当前已创建且可用的原生消息客户端。
     */
    private final AtomicReference<org.eclipse.paho.client.mqttv3.MqttClient> clientRef = new AtomicReference<>();
    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
    private final MQStartupStatus startupStatus = new MQStartupStatus("mqtt");

    /**
     * 双构造 1：注入已初始化的原生 Paho 客户端（用于 runtime 集成自动注入）。
     *
     * @param client 原生 MQTT 客户端
     */
    public MqttMQClient(org.eclipse.paho.client.mqttv3.MqttClient client) {
        this.properties = new MqttMQProperties();
        if (Objects.nonNull(client)) {
            this.clientRef.set(client);
        }
    }

    /**
     * 双构造 2：传入配置，{@link #connection()} 时 lazy 构造原生客户端。
     *
     * @param properties MQTT 配置
     */
    public MqttMQClient(MqttMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 取消指定主题的 MQTT 订阅，并处理原生客户端抛出的异常。
     *
     * @param client 当前使用的原生代理或 Redis 客户端
     * @param topic  要发布、订阅或取消订阅的代理主题
     */
    private static void unsubscribe(org.eclipse.paho.client.mqttv3.MqttClient client, String topic) {
        try {
            client.unsubscribe(topic);
        } catch (Exception exception) {
            throw new IllegalStateException("Unsubscribe MQTT topic failed", exception);
        }
    }

    /**
     * 断开 MQTT 客户端连接，并处理原生客户端抛出的异常。
     *
     * @param client 当前使用的原生代理或 Redis 客户端
     */
    private static void disconnectClient(org.eclipse.paho.client.mqttv3.MqttClient client) {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Disconnect MQTT client failed", exception);
        }
    }

    /**
     * 释放原生客户端，并处理原生客户端抛出的异常。
     *
     * @param client 当前使用的原生代理或 Redis 客户端
     */
    private static void closeClient(org.eclipse.paho.client.mqttv3.MqttClient client) {
        try {
            client.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Close MQTT client failed", exception);
        }
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"mqtt"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "mqtt";
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

    // ========================= 生产者 =========================

    /**
     * 返回消息客户端当前记录的启动状态。
     *
     * @return 当前消息适配器记录的启动状态
     */
    @Override
    public MQStartupStatus startupStatus() {
        return startupStatus;
    }

    // ========================= 消费者 =========================

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
     * MQTT 无原生 broker-side tag selector，仅 topic 通配 → 强制应用层 {@link TagMatcher} 过滤。
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
     * <p>MQTT 实现调用 <code>connection()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        org.eclipse.paho.client.mqttv3.MqttClient client = connection();
        return event -> {
            String payload = serialization().serialize(event);
            String topic = resolveTopic(event, mqProperties);
            try {
                byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                MqttMessage msg = new MqttMessage(body);
                msg.setQos(qos());
                // Paho MQTT v3 没有 user properties；业务稳定 ID 仅保留在 MQEvent payload，
                // MQTT packet id 是短生命周期协议序号，不能作为 ddd4j-message-id 写入或回读。
                client.publish(topic, msg);
            } catch (Exception ex) {
                log.error("Publish MQTT [{}]: {} failed!", topic, payload, ex);
                throw new IllegalStateException("Publish MQTT event failed", ex);
            }
            log.info("Publish MQ [{}]: {}", topic, payload);
        };
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>MQTT 实现调用 <code>connection()</code>。</p>
     *
     * @param listener     监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        org.eclipse.paho.client.mqttv3.MqttClient client = connection();
        // MQTT subscribe 通配：保留原生 subscribe `topic/#` 行为（首个 include tag 拼接到末尾 /#）
        String topic = resolveTopic(listener, mqProperties);
        String includeTag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        String subscribeTopic = Objects.isNull(includeTag) ? topic : topic + "/#";
        client.setManualAcks(true);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String arrivedTopic, MqttMessage message) {
                try {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    MQEvent event = serialization().deserialize(payload, listener.payloadType());
                    if (Objects.isNull(event)) {
                        log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(defaultConcat()));
                        client.messageArrivedComplete(message.getId(), message.getQos());
                        return;
                    }
                    // 应用层 tag 过滤（优先 user property 中的 tag，回落到 topic 末段）
                    if (!TagMatcher.match(readTag(message, arrivedTopic), listener.getTags())) {
                        client.messageArrivedComplete(message.getId(), message.getQos());
                        return;
                    }
                    MqttAcknowledgment ack = new MqttAcknowledgment(message, arrivedTopic);
                    consume(listener, event, ack);
                    if (!ack.isAcknowledged()) {
                        ack.ackSingle();
                    }
                    client.messageArrivedComplete(message.getId(), message.getQos());
                } catch (Throwable ex) {
                    log.error("Consume MQTT [{}] failed", listener.getRouteExpression(defaultConcat()), ex);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        client.subscribe(subscribeTopic, qos());
        lifecycle.register("mqtt-subscription-" + subscribeTopic, () -> unsubscribe(client, subscribeTopic));
        return true;
    }

    /**
     * 从到达的物理 topic 末段解析 tag（保持与生产者 {@link #resolveTopic} 拼接规则一致）。
     *
     * @param message      收到的原生 MQTT 消息
     * @param arrivedTopic 消息抵达时使用的订阅主题
     * @return 解析得到的 MQTT 消息标签
     */
    private String readTag(MqttMessage message, String arrivedTopic) {
        int lastSlash = arrivedTopic.lastIndexOf('/');
        if (lastSlash >= 0) {
            return arrivedTopic.substring(lastSlash + 1);
        }
        return null;
    }

    /**
     * QoS：注入原生客户端时取默认值 1，否则读 properties。
     *
     * @return MQTT 发布或订阅使用的服务质量级别
     */
    private int qos() {
        return properties.getQos();
    }

    /**
     * 取得当前适配器使用的原生连接或客户端，缺少实例时建立连接。
     *
     * @return 供发布或消费操作使用的原生连接或客户端
     */
    private synchronized org.eclipse.paho.client.mqttv3.MqttClient connection() {
        org.eclipse.paho.client.mqttv3.MqttClient c = clientRef.get();
        if (Objects.nonNull(c)) {
            return c;
        }
        try {
            org.eclipse.paho.client.mqttv3.MqttClient nc = new org.eclipse.paho.client.mqttv3.MqttClient(
                    properties.getServerUri(), properties.newClientId());
            nc.connect(properties.connectOptions());
            clientRef.set(nc);
            lifecycle.register("mqtt-client-close", () -> closeClient(nc));
            lifecycle.register("mqtt-client-disconnect", () -> disconnectClient(nc));
            c = nc;
        } catch (Exception ex) {
            throw new IllegalStateException("Open MQTT connection failed", ex);
        }
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
