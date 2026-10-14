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

    @Container
    static final GenericContainer<?> MOSQUITTO = new GenericContainer<>(
            DockerImageName.parse("eclipse-mosquitto:2.0.22"))
            .withCopyToContainer(Transferable.of(
                    "listener 1883 0.0.0.0\nallow_anonymous true\npersistence false\n", 0644),
                    "/mosquitto/config/mosquitto.conf")
            .withExposedPorts(1883)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

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

    public static final class RecordingListener {
        private final CountDownLatch received = new CountDownLatch(1);
        private volatile String messageId;

        public void handle(MQEvent event) {
            messageId = event.getMsgId();
            received.countDown();
        }
    }

    public static final class FailsOnceListener {
        private final CountDownLatch firstAttempt = new CountDownLatch(1);
        private final CountDownLatch secondAttempt = new CountDownLatch(1);
        private final AtomicInteger attempts = new AtomicInteger();

        public void handle(MQEvent event) {
            if (attempts.incrementAndGet() == 1) {
                firstAttempt.countDown();
                throw new IllegalStateException("injected handler failure");
            }
            secondAttempt.countDown();
        }
    }
}
