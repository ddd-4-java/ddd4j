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

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Mosquitto 上的 mica MQTT 适配器发布、消费与连接归属测试。
 */
@Testcontainers(disabledWithoutDocker = true)
class MicaMqttContainerIntegrationTest {

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
    void publishesAndConsumesPayloadIdWithoutClosingInjectedClient() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String topic = "mica" + suffix;
        String messageId = "mica-" + suffix;
        String probeId = "probe-" + suffix;
        MicaMqttProperties properties = new MicaMqttProperties();
        properties.setServerIp(MOSQUITTO.getHost());
        properties.setPort(MOSQUITTO.getMappedPort(1883));
        MqttClient nativeClient = properties.client();
        MicaMqttMQClient client = new MicaMqttMQClient(nativeClient) {
            @Override
            public MQEventSerialization serialization() {
                return new JsonMQEventSerialization();
            }
        };
        RecordingListener bean = new RecordingListener(probeId);
        MQListener listener = new MQListener(bean,
                RecordingListener.class.getMethod("handle", MQEvent.class),
                "ddd4j_mica_" + suffix, "ddd4j", topic, "*",
                Collections.singletonList("*"), "/");
        try {
            assertThat(client.initConsumer(listener, properties)).isTrue();
            // mica subscribe returns before the broker acknowledges the subscription.
            // Confirm delivery with a disposable probe before the single assertion message.
            MQEvent probe = new MQEvent();
            probe.setMsgId(probeId);
            probe.setNamespace("ddd4j");
            probe.setTopic(topic);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            int probeAttempts = 0;
            do {
                probeAttempts++;
                client.initProducer(properties).accept(probe);
            } while (!bean.probeReceived.await(250, TimeUnit.MILLISECONDS)
                    && System.nanoTime() < deadline);
            assertThat(bean.probeReceived.getCount())
                    .as("probe attempts=%s, native connected=%s, broker log=%s",
                            probeAttempts, nativeClient.isConnected(), MOSQUITTO.getLogs())
                    .isZero();

            MQEvent event = new MQEvent();
            event.setMsgId(messageId);
            event.setNamespace("ddd4j");
            event.setTopic(topic);
            event.setCorrelationId("correlation-" + suffix);
            event.setCausationId("cause-" + suffix);

            client.initProducer(properties).accept(event);

            assertThat(bean.received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(bean.messageId).isEqualTo(messageId);
            assertThat(bean.correlationId).isEqualTo(event.getCorrelationId());
            assertThat(bean.causationId).isEqualTo(event.getCausationId());
            assertThat(bean.events.poll(10, TimeUnit.SECONDS)).isNotNull();

            MQEvent correlationOnly = new MQEvent();
            correlationOnly.setMsgId("single-" + suffix);
            correlationOnly.setNamespace("ddd4j");
            correlationOnly.setTopic(topic);
            correlationOnly.setCorrelationId("single-correlation-" + suffix);
            client.initProducer(properties).accept(correlationOnly);
            MQEvent singleReceived = bean.events.poll(10, TimeUnit.SECONDS);
            assertThat(singleReceived).isNotNull();
            assertThat(singleReceived.getMsgId()).isEqualTo(correlationOnly.getMsgId());
            assertThat(singleReceived.getCorrelationId()).isEqualTo(correlationOnly.getCorrelationId());
            assertThat(singleReceived.getCausationId()).isNull();

            MQEvent withoutCausality = new MQEvent();
            withoutCausality.setMsgId("none-" + suffix);
            withoutCausality.setNamespace("ddd4j");
            withoutCausality.setTopic(topic);
            client.initProducer(properties).accept(withoutCausality);
            MQEvent plainReceived = bean.events.poll(10, TimeUnit.SECONDS);
            assertThat(plainReceived).isNotNull();
            assertThat(plainReceived.getMsgId()).isEqualTo(withoutCausality.getMsgId());
            assertThat(plainReceived.getCorrelationId()).isNull();
            assertThat(plainReceived.getCausationId()).isNull();
        } finally {
            try {
                client.close();
                assertThat(nativeClient.isConnected()).isTrue();
            } finally {
                if (nativeClient.isConnected()) {
                    nativeClient.disconnect();
                }
                nativeClient.stop();
            }
        }
    }

    public static final class RecordingListener {
        private final String probeId;
        private final CountDownLatch probeReceived = new CountDownLatch(1);
        private final CountDownLatch received = new CountDownLatch(1);
        private volatile String messageId;
        private volatile String correlationId;
        private volatile String causationId;
        private final LinkedBlockingQueue<MQEvent> events = new LinkedBlockingQueue<>();

        private RecordingListener(String probeId) {
            this.probeId = probeId;
        }

        public void handle(MQEvent event) {
            if (probeId.equals(event.getMsgId())) {
                probeReceived.countDown();
                return;
            }
            messageId = event.getMsgId();
            correlationId = event.getCorrelationId();
            causationId = event.getCausationId();
            events.add(event);
            received.countDown();
        }
    }
}
