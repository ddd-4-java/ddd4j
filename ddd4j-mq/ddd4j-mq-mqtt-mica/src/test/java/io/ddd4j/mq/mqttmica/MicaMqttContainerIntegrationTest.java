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

    /** \u4fdd\u5b58\u76d1\u542c\u56de\u8c03\u7ed3\u679c\uff0c\u4f9b\u65ad\u8a00\u68c0\u67e5\u4e8b\u4ef6\u4f20\u9012\u884c\u4e3a\u3002 */
    public static final class RecordingListener {
        /** \u7528\u6765\u8bc6\u522b\u6d4b\u8bd5\u63a2\u9488\u4efb\u52a1\u7684\u7f16\u53f7\u3002 */
        private final String probeId;
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final CountDownLatch probeReceived = new CountDownLatch(1);
        /** \u7b49\u5f85\u6570\u636e\u5230\u8fbe\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch received = new CountDownLatch(1);
        /** \u5f53\u524d\u6d4b\u8bd5\u671f\u95f4\u6536\u96c6\u7684\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u6bd4\u8f83\u53d1\u5e03\u5185\u5bb9\u6216\u987a\u5e8f\u3002 */
        private final LinkedBlockingQueue<MQEvent> events = new LinkedBlockingQueue<>();
        /** \u6d88\u606f\u6807\u8bc6\u3002 */
        private volatile String messageId;
        /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
        private volatile String correlationId;
        /** \u5f53\u524d\u4e8b\u4ef6\u643a\u5e26\u7684\u4e0a\u6e38\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private volatile String causationId;

        /**
         * \u8bbe\u7f6e <code>probeId</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>RecordingListener</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param probeId \u7528\u4e8e\u521d\u59cb\u5316 <code>RecordingListener</code> \u7684 <code>String</code> \u503c
         */
        private RecordingListener(String probeId) {
            this.probeId = probeId;
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>messageId = event.getMsgId()</code>\u3001<code>correlationId = event.getCorrelationId()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
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
