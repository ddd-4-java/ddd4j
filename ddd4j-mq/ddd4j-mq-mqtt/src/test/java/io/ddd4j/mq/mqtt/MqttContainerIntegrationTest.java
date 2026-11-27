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

    /** \u4e3a\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u9694\u79bb <code>Generic</code> \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
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
     * \u9a8c\u8bc1<code>client.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>bean.received.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>nativeClient.isConnected()</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>client.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>bean.firstAttempt.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>bean.attempts.get()</code> \u7b49\u4e8e <code>2</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
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

    /** \u4fdd\u5b58\u76d1\u542c\u56de\u8c03\u7ed3\u679c\uff0c\u4f9b\u65ad\u8a00\u68c0\u67e5\u4e8b\u4ef6\u4f20\u9012\u884c\u4e3a\u3002 */
    public static final class RecordingListener {
        /** \u7b49\u5f85\u6570\u636e\u5230\u8fbe\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch received = new CountDownLatch(1);
        /** \u6d88\u606f\u6807\u8bc6\u3002 */
        private volatile String messageId;

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>messageId = event.getMsgId()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void handle(MQEvent event) {
            messageId = event.getMsgId();
            received.countDown();
        }
    }

    /** \u5c06 <code>firstAttempt</code>\u3001<code>secondAttempt</code>\u3001<code>attempts</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class FailsOnceListener {
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final CountDownLatch firstAttempt = new CountDownLatch(1);
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final CountDownLatch secondAttempt = new CountDownLatch(1);
        /** \u8ddf\u8e2a\u6d4b\u8bd5\u64cd\u4f5c\u7684\u8c03\u7528\u6216\u91cd\u8bd5\u6b21\u6570\u3002 */
        private final AtomicInteger attempts = new AtomicInteger();

        /**
         * \u8c03\u7528 <code>attempts.incrementAndGet()</code>\u3001<code>firstAttempt.countDown()</code>\u3001<code>secondAttempt.countDown()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
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
