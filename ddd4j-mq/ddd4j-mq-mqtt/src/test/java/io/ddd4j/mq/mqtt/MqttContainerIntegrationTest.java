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

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MQTT 3.1.1 Broker 上的适配器发布、消费和连接归属测试。
 */
@Testcontainers(disabledWithoutDocker = true)
class MqttContainerIntegrationTest {

    /**
     * 为集成测试提供隔离 <code>Generic</code> 服务的容器。
     */
    @Container
    static final GenericContainer<?> MOSQUITTO = new GenericContainer<>(
            DockerImageName.parse("eclipse-mosquitto:2.0.22"))
            .withCopyToContainer(Transferable.of(
                            "listener 1883 0.0.0.0\nallow_anonymous true\npersistence false\n", 0644),
                    "/mosquitto/config/mosquitto.conf")
            .withExposedPorts(1883)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    /**
     * 验证<code>client.initConsumer(listener, properties)</code>为真，<code>bean.received.await(10, TimeUnit.SECONDS)</code>为真，<code>nativeClient.isConnected()</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void publishesAndConsumesPayloadIdWithoutTakingOwnershipOfInjectedClient() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String topic = "orders" + suffix;
        String messageId = "mqtt-" + suffix;
        String uri = "tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(1883);
        MqttMQProperties properties = new MqttMQProperties();
        properties.setServerUri(uri);
        MqttClient nativeClient = new MqttClient(uri, "ddd4j-" + suffix, new MemoryPersistence());
        nativeClient.connect(properties.connectOptions());
        MqttClient observer = new MqttClient(uri, "ddd4j-observer-" + suffix, new MemoryPersistence());

        MqttMQClient client = new MqttMQClient(nativeClient) {
            @Override
            public MQEventSerialization serialization() {
                return new JsonMQEventSerialization();
            }
        };
        RecordingListener bean = new RecordingListener();
        MQListener listener = new MQListener(bean,
                RecordingListener.class.getMethod("handle", MQEvent.class),
                "ddd4j_group_" + suffix, "ddd4j", topic, "*",
                Collections.singletonList("*"), "/");
        CountDownLatch observed = new CountDownLatch(1);
        AtomicInteger observedQos = new AtomicInteger(-1);
        AtomicReference<String> observedPayload = new AtomicReference<>();
        try {
            observer.connect(properties.connectOptions());
            observer.subscribe("ddd4j/" + topic, 1, (arrivedTopic, message) -> {
                observedQos.set(message.getQos());
                observedPayload.set(new String(message.getPayload(), StandardCharsets.UTF_8));
                observed.countDown();
            });
            assertThat(client.initConsumer(listener, properties)).isTrue();
            MQEvent event = new MQEvent();
            event.setMsgId(messageId);
            event.setNamespace("ddd4j");
            event.setTopic(topic);

            client.initProducer(properties).accept(event);

            assertThat(bean.received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(bean.messageId).isEqualTo(messageId);
            assertThat(observed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(observedQos.get()).isEqualTo(1);
            assertThat(observedPayload.get()).contains(messageId);
        } finally {
            try {
                client.close();
                assertThat(nativeClient.isConnected()).isTrue();
            } finally {
                if (observer.isConnected()) {
                    observer.disconnect();
                }
                observer.close();
                if (nativeClient.isConnected()) {
                    nativeClient.disconnect();
                }
                nativeClient.close();
            }
        }
    }

    /**
     * 验证<code>client.initConsumer(listener, properties)</code>为真，<code>bean.firstAttempt.await(10, TimeUnit.SECONDS)</code>为真，<code>bean.attempts.get()</code> 等于 <code>2</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void failedQosOneHandlerIsRedeliveredAfterPersistentSessionReconnect() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String topic = "retry" + suffix;
        String uri = "tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(1883);
        MqttMQProperties properties = new MqttMQProperties();
        properties.setServerUri(uri);
        properties.setCleanSession(false);
        properties.setAutomaticReconnect(false);
        MqttClient nativeClient = new MqttClient(uri, "ddd4j-retry-" + suffix, new MemoryPersistence());
        nativeClient.connect(properties.connectOptions());
        MqttMQClient client = new MqttMQClient(nativeClient) {
            @Override
            public MQEventSerialization serialization() {
                return new JsonMQEventSerialization();
            }
        };
        FailsOnceListener bean = new FailsOnceListener();
        MQListener listener = new MQListener(bean,
                FailsOnceListener.class.getMethod("handle", MQEvent.class),
                "ddd4j_retry_" + suffix, "ddd4j", topic, "*",
                Collections.singletonList("*"), "/");
        try {
            assertThat(client.initConsumer(listener, properties)).isTrue();
            MQEvent event = new MQEvent();
            event.setMsgId("retry-" + suffix);
            event.setNamespace("ddd4j");
            event.setTopic(topic);
            client.initProducer(properties).accept(event);

            assertThat(bean.firstAttempt.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            nativeClient.disconnectForcibly(0, 0, false);
            nativeClient.connect(properties.connectOptions());

            assertThat(bean.secondAttempt.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(bean.attempts.get()).isEqualTo(2);
        } finally {
            try {
                client.close();
            } finally {
                if (nativeClient.isConnected()) {
                    nativeClient.disconnect();
                }
                nativeClient.close();
            }
        }
    }

    /**
     * 保存监听回调结果，供断言检查事件传递行为。
     */
    public static final class RecordingListener {
        /**
         * 等待数据到达的同步门闩。
         */
        private final CountDownLatch received = new CountDownLatch(1);
        /**
         * 消息标识。
         */
        private volatile String messageId;

        /**
         * 根据本次调用更新 <code>messageId = event.getMsgId()</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        public void handle(MQEvent event) {
            messageId = event.getMsgId();
            received.countDown();
        }
    }

    /**
     * 将 <code>firstAttempt</code>、<code>secondAttempt</code>、<code>attempts</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class FailsOnceListener {
        /**
         * 协调测试线程在指定执行阶段等待或继续的门闩。
         */
        private final CountDownLatch firstAttempt = new CountDownLatch(1);
        /**
         * 协调测试线程在指定执行阶段等待或继续的门闩。
         */
        private final CountDownLatch secondAttempt = new CountDownLatch(1);
        /**
         * 跟踪测试操作的调用或重试次数。
         */
        private final AtomicInteger attempts = new AtomicInteger();

        /**
         * 调用 <code>attempts.incrementAndGet()</code>、<code>firstAttempt.countDown()</code>、<code>secondAttempt.countDown()</code> 执行当前夹具操作。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        public void handle(MQEvent event) {
            if (attempts.incrementAndGet() == 1) {
                firstAttempt.countDown();
                throw new IllegalStateException("injected handler failure");
            }
            secondAttempt.countDown();
        }
    }
}
