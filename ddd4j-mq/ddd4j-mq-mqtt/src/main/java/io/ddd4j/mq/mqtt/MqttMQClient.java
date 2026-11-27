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

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final MqttMQProperties properties;
    /** \u6301\u6709\u5f53\u524d\u5df2\u521b\u5efa\u4e14\u53ef\u7528\u7684\u539f\u751f\u6d88\u606f\u5ba2\u6237\u7aef\u3002 */
    private final AtomicReference<org.eclipse.paho.client.mqttv3.MqttClient> clientRef = new AtomicReference<>();
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
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
     * \u53d6\u6d88\u6307\u5b9a\u4e3b\u9898\u7684 MQTT \u8ba2\u9605\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param client \u5f53\u524d\u4f7f\u7528\u7684\u539f\u751f\u4ee3\u7406\u6216 Redis \u5ba2\u6237\u7aef
     * @param topic \u8981\u53d1\u5e03\u3001\u8ba2\u9605\u6216\u53d6\u6d88\u8ba2\u9605\u7684\u4ee3\u7406\u4e3b\u9898
     */
    private static void unsubscribe(org.eclipse.paho.client.mqttv3.MqttClient client, String topic) {
        try {
            client.unsubscribe(topic);
        } catch (Exception exception) {
            throw new IllegalStateException("Unsubscribe MQTT topic failed", exception);
        }
    }

    /**
     * \u65ad\u5f00 MQTT \u5ba2\u6237\u7aef\u8fde\u63a5\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param client \u5f53\u524d\u4f7f\u7528\u7684\u539f\u751f\u4ee3\u7406\u6216 Redis \u5ba2\u6237\u7aef
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
     * \u91ca\u653e\u539f\u751f\u5ba2\u6237\u7aef\uff0c\u5e76\u5904\u7406\u539f\u751f\u5ba2\u6237\u7aef\u629b\u51fa\u7684\u5f02\u5e38\u3002
     * @param client \u5f53\u524d\u4f7f\u7528\u7684\u539f\u751f\u4ee3\u7406\u6216 Redis \u5ba2\u6237\u7aef
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
     * MQTT \u65e0\u539f\u751f broker-side tag selector\uff0c\u4ec5 topic \u901a\u914d \u2192 \u5f3a\u5236\u5e94\u7528\u5c42 {@link TagMatcher} \u8fc7\u6ee4\u3002
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
     * @param listener 监听器定义
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
     * \u4ece\u5230\u8fbe\u7684\u7269\u7406 topic \u672b\u6bb5\u89e3\u6790 tag\uff08\u4fdd\u6301\u4e0e\u751f\u4ea7\u8005 {@link #resolveTopic} \u62fc\u63a5\u89c4\u5219\u4e00\u81f4\uff09\u3002
     *
     * @param message \u6536\u5230\u7684\u539f\u751f MQTT \u6d88\u606f
     * @param arrivedTopic \u6d88\u606f\u62b5\u8fbe\u65f6\u4f7f\u7528\u7684\u8ba2\u9605\u4e3b\u9898
     * @return \u89e3\u6790\u5f97\u5230\u7684 MQTT \u6d88\u606f\u6807\u7b7e
     */
    private String readTag(MqttMessage message, String arrivedTopic) {
        int lastSlash = arrivedTopic.lastIndexOf('/');
        if (lastSlash >= 0) {
            return arrivedTopic.substring(lastSlash + 1);
        }
        return null;
    }

    /**
     * QoS\uff1a\u6ce8\u5165\u539f\u751f\u5ba2\u6237\u7aef\u65f6\u53d6\u9ed8\u8ba4\u503c 1\uff0c\u5426\u5219\u8bfb properties\u3002
     *
     * @return MQTT \u53d1\u5e03\u6216\u8ba2\u9605\u4f7f\u7528\u7684\u670d\u52a1\u8d28\u91cf\u7ea7\u522b
     */
    private int qos() {
        return properties.getQos();
    }

    /**
     * \u53d6\u5f97\u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef\uff0c\u7f3a\u5c11\u5b9e\u4f8b\u65f6\u5efa\u7acb\u8fde\u63a5\u3002
     * @return \u4f9b\u53d1\u5e03\u6216\u6d88\u8d39\u64cd\u4f5c\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef
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
