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

import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NATS JetStream 容器轨：验证 ddd4j 配置连接与持久化发布确认。
 */
@Testcontainers(disabledWithoutDocker = true)
class NatsJetStreamIntegrationTest {

    /**
     * 为集成测试提供隔离 <code>Generic</code> 服务的容器。
     */
    @Container
    static final GenericContainer<?> NATS = new GenericContainer<>(
            DockerImageName.parse("nats:2.14.6-alpine"))
            .withTmpFs(Collections.singletonMap("/data", "rw,size=512m"))
            .withCommand("-js", "-sd", "/data")
            .withExposedPorts(4222)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    /**
     * 验证<code>connection.getStatus()</code> 等于 <code>Connection.Status.CONNECTED</code>，<code>ack.getSeqno()</code> 等于 <code>1L</code>。
     *
     * @throws Exception 装配运行测试所需的组件时失败
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
     * 验证<code>received</code>非空，<code>connection.getStatus()</code> 等于 <code>Connection.Status.CONNECTED</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 验证<code>client.initConsumer(listener, properties)</code>为真，<code>bean.received.await(10, TimeUnit.SECONDS)</code>为真，<code>connection.getStatus()</code> 等于 <code>Connection.Status.CONNECTED</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>client.initConsumer(listener, properties)</code>为真，<code>bean.received.await(10, TimeUnit.SECONDS)</code>为真，<code>bean.causationId</code> 等于 <code>"native-causation"</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>client.initConsumer(listener, properties)</code>为真，<code>bean.receivedTwice.await(10, TimeUnit.SECONDS)</code>为真，<code>acknowledged</code> 等于 <code>2L</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>client.initConsumer(listener, properties)</code>为真，<code>bean.received.await(10, TimeUnit.SECONDS)</code>为真，<code>bean.tags</code> 按顺序仅包含 <code>"allowed"</code>、<code>"&lt;none&gt;"</code>、<code>"allowed"</code>。
     *
     * @throws Exception 等待并发操作时失败
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

    /**
     * 将 <code>received</code>、<code>tags</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class TagFilteringListener {
        /**
         * 等待数据到达的同步门闩。
         */
        private final CountDownLatch received = new CountDownLatch(3);
        /**
         * 投递或订阅用例使用的标签表达式。
         */
        private final List<String> tags = new CopyOnWriteArrayList<>();

        /**
         * 调用 <code>tags.add()</code>、<code>event.getTag()</code>、<code>received.countDown()</code> 执行当前夹具操作。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        public void handle(MQEvent event) {
            tags.add(event.getTag() == null ? "<none>" : event.getTag());
            received.countDown();
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
         * 用于关联同一次操作产生的事件或消息的标识。
         */
        private volatile String correlationId;
        /**
         * 当前事件携带的上游事件标识。
         */
        private volatile String causationId;

        /**
         * 根据本次调用更新 <code>messageId = event.getMsgId()</code>、<code>correlationId = event.getCorrelationId()</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        public void handle(MQEvent event) {
            messageId = event.getMsgId();
            correlationId = event.getCorrelationId();
            causationId = event.getCausationId();
            received.countDown();
        }
    }

    /**
     * 将 <code>attempts</code>、<code>receivedTwice</code>、<code>firstMessageId</code>、<code>secondMessageId</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class FailOnceListener {
        /**
         * 跟踪测试操作的调用或重试次数。
         */
        private final AtomicInteger attempts = new AtomicInteger();
        /**
         * 协调测试线程在指定执行阶段等待或继续的门闩。
         */
        private final CountDownLatch receivedTwice = new CountDownLatch(1);
        /**
         * 首次投递记录用来定位该消息的标识。
         */
        private volatile String firstMessageId;
        /**
         * 第二条测试投递的业务消息标识。
         */
        private volatile String secondMessageId;
        /**
         * 用来区分第二次请求或事件的关联标识。
         */
        private volatile String secondCorrelationId;

        /**
         * 根据本次调用更新 <code>firstMessageId = event.getMsgId()</code>、<code>secondMessageId = event.getMsgId()</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
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
