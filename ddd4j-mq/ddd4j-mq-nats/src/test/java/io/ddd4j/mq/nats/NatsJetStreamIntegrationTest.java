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
package io.ddd4j.mq.nats;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NATS JetStream 容器轨：验证 ddd4j 配置连接与持久化发布确认。
 */
@Testcontainers(disabledWithoutDocker = true)
class NatsJetStreamIntegrationTest {

    /** \u4e3a\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u9694\u79bb <code>Generic</code> \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final GenericContainer<?> NATS = new GenericContainer<>(
            DockerImageName.parse("nats:2.14.6-alpine"))
            .withTmpFs(Collections.singletonMap("/data", "rw,size=512m"))
            .withCommand("-js", "-sd", "/data")
            .withExposedPorts(4222)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    /**
     * \u9a8c\u8bc1<code>connection.getStatus()</code> \u7b49\u4e8e <code>Connection.Status.CONNECTED</code>\uff0c<code>ack.getSeqno()</code> \u7b49\u4e8e <code>1L</code>\u3002
     * @throws Exception \u88c5\u914d\u8fd0\u884c\u6d4b\u8bd5\u6240\u9700\u7684\u7ec4\u4ef6\u65f6\u5931\u8d25
     */
    @Test
    void natsPropertiesConnectsAndJetStreamAcknowledgesPublish() throws Exception {
        NatsProperties properties = new NatsProperties();
        properties.setServers("nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222));
        properties.setConnectionName("ddd4j-nats-it");
        properties.setConnectTimeoutMillis(10_000L);

        try (Connection connection = properties.connect()) {
            String suffix = Long.toUnsignedString(System.nanoTime());
            String stream = "DDD4J_" + suffix;
            String subject = "ddd4j.it.nats." + suffix;
            JetStreamManagement management = connection.jetStreamManagement();
            management.addStream(StreamConfiguration.builder().name(stream).subjects(subject).build());

            PublishAck ack = connection.jetStream().publish(subject,
                    "ddd4j-nats-payload".getBytes(StandardCharsets.UTF_8));

            assertThat(connection.getStatus()).isEqualTo(Connection.Status.CONNECTED);
            assertThat(ack.getStream()).isEqualTo(stream);
            assertThat(ack.getSeqno()).isEqualTo(1L);
        }
    }

    /**
     * \u9a8c\u8bc1<code>received</code>\u975e\u7a7a\uff0c<code>connection.getStatus()</code> \u7b49\u4e8e <code>Connection.Status.CONNECTED</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void natsAdapterPublishesStableHeaderThroughJetStream() throws Exception {
        NatsProperties properties = new NatsProperties();
        properties.setServers("nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222));
        String suffix = Long.toUnsignedString(System.nanoTime());
        String topic = "native" + suffix;
        String subject = "ddd4j." + topic;
        String messageId = "nats-" + suffix;

        try (Connection connection = properties.connect()) {
            connection.jetStreamManagement().addStream(
                    StreamConfiguration.builder().name("DDD4J_" + suffix).subjects(subject).build());
            JetStreamSubscription subscription = connection.jetStream().subscribe(subject);
            NatsMQClient client = new NatsMQClient(connection) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            try {
                MQEvent event = new MQEvent();
                event.setMsgId(messageId);
                event.setNamespace("ddd4j");
                event.setTopic(topic);
                event.setCorrelationId("correlation-" + suffix);
                event.setCausationId("causation-" + suffix);

                client.initProducer(properties).accept(event);
                Message received = subscription.nextMessage(Duration.ofSeconds(5));

                assertThat(received).isNotNull();
                assertThat(received.getHeaders().getFirst(MessageHeaders.HEADER_MESSAGE_ID))
                        .isEqualTo(messageId);
                assertThat(received.getHeaders().getFirst(MQDeliveryHeaders.CORRELATION_ID))
                        .isEqualTo("correlation-" + suffix);
                assertThat(received.getHeaders().getFirst(MQDeliveryHeaders.CAUSATION_ID))
                        .isEqualTo("causation-" + suffix);
                assertThat(new String(received.getData(), StandardCharsets.UTF_8))
                        .contains(messageId);
                received.ack();
            } finally {
                client.close();
            }
            assertThat(connection.getStatus()).isEqualTo(Connection.Status.CONNECTED);
        }
    }

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>bean.received.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>connection.getStatus()</code> \u7b49\u4e8e <code>Connection.Status.CONNECTED</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void natsAdapterConsumesJetStreamMessageAndKeepsInjectedConnectionOpen() throws Exception {
        NatsProperties properties = new NatsProperties();
        properties.setServers("nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222));
        String suffix = Long.toUnsignedString(System.nanoTime());
        String topic = "consume" + suffix;
        String subject = "ddd4j." + topic;
        String messageId = "nats-consume-" + suffix;
        String stream = "DDD4J_" + suffix;
        String group = "ddd4j_group_" + suffix;

        try (Connection connection = properties.connect()) {
            connection.jetStreamManagement().addStream(
                    StreamConfiguration.builder().name(stream).subjects(subject).build());
            NatsMQClient client = new NatsMQClient(connection) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            RecordingListener bean = new RecordingListener();
            MQListener listener = new MQListener(bean,
                    RecordingListener.class.getMethod("handle", MQEvent.class),
                    group, "ddd4j", topic, "*",
                    Collections.singletonList("*"), ".");
            try {
                assertThat(client.initConsumer(listener, properties)).isTrue();
                MQEvent event = new MQEvent();
                event.setMsgId(messageId);
                event.setNamespace("ddd4j");
                event.setTopic(topic);

                client.initProducer(properties).accept(event);

                assertThat(bean.received.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(bean.messageId).isEqualTo(messageId);
                long acknowledged = 0;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (acknowledged == 0 && System.nanoTime() < deadline) {
                    acknowledged = connection.jetStreamManagement()
                            .getConsumerInfo(stream, group).getAckFloor().getConsumerSequence();
                    if (acknowledged == 0) {
                        Thread.sleep(25);
                    }
                }
                assertThat(acknowledged).isEqualTo(1L);
            } finally {
                client.close();
            }
            assertThat(connection.getStatus()).isEqualTo(Connection.Status.CONNECTED);
        }
    }

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>bean.received.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>bean.causationId</code> \u7b49\u4e8e <code>"native-causation"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void natsAdapterPrefersNativeCausalityHeadersOverPayloadMirror() throws Exception {
        NatsProperties properties = new NatsProperties();
        properties.setServers("nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222));
        String suffix = Long.toUnsignedString(System.nanoTime());
        String topic = "causality" + suffix;
        String subject = "ddd4j." + topic;
        String group = "ddd4j_group_" + suffix;

        try (Connection connection = properties.connect()) {
            connection.jetStreamManagement().addStream(StreamConfiguration.builder()
                    .name("DDD4J_" + suffix).subjects(subject).build());
            NatsMQClient client = new NatsMQClient(connection) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            RecordingListener bean = new RecordingListener();
            MQListener listener = new MQListener(bean,
                    RecordingListener.class.getMethod("handle", MQEvent.class),
                    group, "ddd4j", topic, "*", Collections.singletonList("*"), ".");
            try {
                assertThat(client.initConsumer(listener, properties)).isTrue();
                MQEvent event = new MQEvent();
                event.setMsgId("stable-" + suffix);
                event.setNamespace("ddd4j");
                event.setTopic(topic);
                event.setCorrelationId("payload-correlation");
                event.setCausationId("payload-causation");
                Headers headers = new Headers();
                headers.put(MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
                headers.put(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
                headers.put(MQDeliveryHeaders.CAUSATION_ID, "native-causation");
                String payload = new JsonMQEventSerialization().serialize(event);

                connection.jetStream().publish(subject, headers, payload.getBytes(StandardCharsets.UTF_8));

                assertThat(bean.received.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(bean.correlationId).isEqualTo("native-correlation");
                assertThat(bean.causationId).isEqualTo("native-causation");
            } finally {
                client.close();
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>bean.receivedTwice.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>acknowledged</code> \u7b49\u4e8e <code>2L</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void natsAdapterRedeliversAfterListenerFailureAndAcknowledgesSuccess() throws Exception {
        NatsProperties properties = new NatsProperties();
        properties.setServers("nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222));
        String suffix = Long.toUnsignedString(System.nanoTime());
        String topic = "retry" + suffix;
        String subject = "ddd4j." + topic;
        String group = "ddd4j_group_" + suffix;
        String stream = "DDD4J_" + suffix;

        try (Connection connection = properties.connect()) {
            connection.jetStreamManagement().addStream(StreamConfiguration.builder()
                    .name(stream).subjects(subject).build());
            NatsMQClient client = new NatsMQClient(connection) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            FailOnceListener bean = new FailOnceListener();
            MQListener listener = new MQListener(bean,
                    FailOnceListener.class.getMethod("handle", MQEvent.class),
                    group, "ddd4j", topic, "*", Collections.singletonList("*"), ".");
            try {
                assertThat(client.initConsumer(listener, properties)).isTrue();
                MQEvent event = new MQEvent();
                event.setMsgId("retry-" + suffix);
                event.setNamespace("ddd4j");
                event.setTopic(topic);
                event.setCorrelationId("correlation-" + suffix);

                client.initProducer(properties).accept(event);

                assertThat(bean.receivedTwice.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(bean.attempts.get()).isEqualTo(2);
                assertThat(bean.firstMessageId).isEqualTo(event.getMsgId());
                assertThat(bean.secondMessageId).isEqualTo(event.getMsgId());
                assertThat(bean.secondCorrelationId).isEqualTo(event.getCorrelationId());
                long acknowledged = 0;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (acknowledged == 0 && System.nanoTime() < deadline) {
                    acknowledged = connection.jetStreamManagement()
                            .getConsumerInfo(stream, group).getAckFloor().getConsumerSequence();
                    if (acknowledged == 0) {
                        Thread.sleep(25);
                    }
                }
                assertThat(acknowledged).isEqualTo(2L);
            } finally {
                client.close();
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>bean.received.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>bean.tags</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"allowed"</code>\u3001<code>"&lt;none&gt;"</code>\u3001<code>"allowed"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void natsAdapterRoutesTaggedAndUntaggedEventsThroughOneLogicalTopic() throws Exception {
        NatsProperties properties = new NatsProperties();
        properties.setServers("nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222));
        String suffix = Long.toUnsignedString(System.nanoTime());
        String topic = "tags" + suffix;
        String subject = "ddd4j." + topic;
        String group = "ddd4j_group_" + suffix;
        String stream = "DDD4J_" + suffix;

        try (Connection connection = properties.connect()) {
            connection.jetStreamManagement().addStream(StreamConfiguration.builder()
                    .name(stream).subjects(subject, subject + ".*").build());
            NatsMQClient client = new NatsMQClient(connection) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            TagFilteringListener bean = new TagFilteringListener();
            MQListener listener = new MQListener(bean,
                    TagFilteringListener.class.getMethod("handle", MQEvent.class),
                    group, "ddd4j", topic, "* -blocked", Collections.singletonList("*"), ".");
            try {
                assertThat(client.initConsumer(listener, properties)).isTrue();
                java.util.function.Consumer<MQEvent> publisher = client.initProducer(properties);
                for (String tag : Arrays.asList("blocked", "allowed", null)) {
                    MQEvent event = new MQEvent();
                    event.setMsgId("tag-" + suffix + "-" + tag);
                    event.setNamespace("ddd4j");
                    event.setTopic(topic);
                    event.setTag(tag);
                    publisher.accept(event);
                }
                MQEvent conflictingPayload = new MQEvent();
                conflictingPayload.setMsgId("native-tag-" + suffix);
                conflictingPayload.setNamespace("ddd4j");
                conflictingPayload.setTopic(topic);
                conflictingPayload.setTag("blocked");
                Headers nativeHeaders = NatsMQClient.messageHeaders(conflictingPayload);
                nativeHeaders.put(MessageHeaders.HEADER_DESTINATION_TAG, "allowed");
                String conflictingPayloadJson = new JsonMQEventSerialization().serialize(conflictingPayload);
                connection.jetStream().publish(subject, nativeHeaders,
                        conflictingPayloadJson.getBytes(StandardCharsets.UTF_8));

                assertThat(bean.received.await(10, TimeUnit.SECONDS)).isTrue();
                long acknowledged = 0;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (acknowledged < 4 && System.nanoTime() < deadline) {
                    acknowledged = connection.jetStreamManagement()
                            .getConsumerInfo(stream, group).getAckFloor().getConsumerSequence();
                    if (acknowledged < 4) {
                        Thread.sleep(25);
                    }
                }
                assertThat(acknowledged).isEqualTo(4L);
                assertThat(bean.tags).containsExactly("allowed", "<none>", "allowed");
            } finally {
                client.close();
            }
        }
    }

    /** \u5c06 <code>received</code>\u3001<code>tags</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class TagFilteringListener {
        /** \u7b49\u5f85\u6570\u636e\u5230\u8fbe\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch received = new CountDownLatch(3);
        /** \u6295\u9012\u6216\u8ba2\u9605\u7528\u4f8b\u4f7f\u7528\u7684\u6807\u7b7e\u8868\u8fbe\u5f0f\u3002 */
        private final List<String> tags = new CopyOnWriteArrayList<>();

        /**
         * \u8c03\u7528 <code>tags.add()</code>\u3001<code>event.getTag()</code>\u3001<code>received.countDown()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void handle(MQEvent event) {
            tags.add(event.getTag() == null ? "<none>" : event.getTag());
            received.countDown();
        }
    }

    /** \u4fdd\u5b58\u76d1\u542c\u56de\u8c03\u7ed3\u679c\uff0c\u4f9b\u65ad\u8a00\u68c0\u67e5\u4e8b\u4ef6\u4f20\u9012\u884c\u4e3a\u3002 */
    public static final class RecordingListener {
        /** \u7b49\u5f85\u6570\u636e\u5230\u8fbe\u7684\u540c\u6b65\u95e8\u95e9\u3002 */
        private final CountDownLatch received = new CountDownLatch(1);
        /** \u6d88\u606f\u6807\u8bc6\u3002 */
        private volatile String messageId;
        /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
        private volatile String correlationId;
        /** \u5f53\u524d\u4e8b\u4ef6\u643a\u5e26\u7684\u4e0a\u6e38\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private volatile String causationId;

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>messageId = event.getMsgId()</code>\u3001<code>correlationId = event.getCorrelationId()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void handle(MQEvent event) {
            messageId = event.getMsgId();
            correlationId = event.getCorrelationId();
            causationId = event.getCausationId();
            received.countDown();
        }
    }

    /** \u5c06 <code>attempts</code>\u3001<code>receivedTwice</code>\u3001<code>firstMessageId</code>\u3001<code>secondMessageId</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class FailOnceListener {
        /** \u8ddf\u8e2a\u6d4b\u8bd5\u64cd\u4f5c\u7684\u8c03\u7528\u6216\u91cd\u8bd5\u6b21\u6570\u3002 */
        private final AtomicInteger attempts = new AtomicInteger();
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final CountDownLatch receivedTwice = new CountDownLatch(1);
        /** \u9996\u6b21\u6295\u9012\u8bb0\u5f55\u7528\u6765\u5b9a\u4f4d\u8be5\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
        private volatile String firstMessageId;
        /** \u7b2c\u4e8c\u6761\u6d4b\u8bd5\u6295\u9012\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\u3002 */
        private volatile String secondMessageId;
        /** \u7528\u6765\u533a\u5206\u7b2c\u4e8c\u6b21\u8bf7\u6c42\u6216\u4e8b\u4ef6\u7684\u5173\u8054\u6807\u8bc6\u3002 */
        private volatile String secondCorrelationId;

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>firstMessageId = event.getMsgId()</code>\u3001<code>secondMessageId = event.getMsgId()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void handle(MQEvent event) {
            if (attempts.incrementAndGet() == 1) {
                firstMessageId = event.getMsgId();
                throw new IllegalStateException("first delivery fails");
            }
            secondMessageId = event.getMsgId();
            secondCorrelationId = event.getCorrelationId();
            receivedTwice.countDown();
        }
    }
}
