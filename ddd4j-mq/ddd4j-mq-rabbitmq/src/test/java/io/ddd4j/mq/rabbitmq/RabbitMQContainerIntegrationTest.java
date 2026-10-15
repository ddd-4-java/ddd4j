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
package io.ddd4j.mq.rabbitmq;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.lifecycle.MQInitializationException;
import io.ddd4j.mq.lifecycle.MQStartupState;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RabbitMQ testcontainers 集成测试：使用官方 {@link RabbitMQContainer} 启动真实 broker，
 * 验证 ddd4j 的 RabbitMQProperties / RabbitMQClient 在真实 AMQP broker 上的兼容性。
 *
 * <p>本测试覆盖：
 * <ul>
 *   <li>RabbitMQProperties 连接真实 RabbitMQ 镜像（验证 host/port/credential 配置正确）</li>
 *   <li>amqp-client 与 RabbitMQ 4.1.8-management 协议互通</li>
 *   <li>messageId 与自定义 header 在 broker 上保留完整（ddd4j 消息头兼容）</li>
 * </ul>
 *
 * <p>测试同时覆盖原生消息头往返、RabbitMQClient 的反射消费者失败重投与关闭后发布失败。
 *
 * <p>在 CI {@code infrastructure-integration} job（Docker daemon）下运行；本地需 Docker 环境。
 */
@Testcontainers(disabledWithoutDocker = true)
class RabbitMQContainerIntegrationTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1.8-management")
            .withEnv("RABBITMQ_ERLANG_COOKIE", "ddd4j-test-cookie-for-rabbitmq")
            .withTmpFs(Collections.singletonMap("/var/lib/rabbitmq", "rw,size=512m"))
            .withCopyToContainer(Transferable.of("ddd4j-test-cookie-for-rabbitmq", 0600),
                    "/var/lib/rabbitmq/.erlang.cookie");
    private static final String EXCHANGE = "ddd4j.it.rabbit";
    private static final String QUEUE = "ddd4j.it.rabbit.verify";

    private static RabbitMQProperties brokerProperties() {
        RabbitMQProperties properties = new RabbitMQProperties();
        properties.setHost(RABBIT.getHost());
        properties.setPort(RABBIT.getAmqpPort());
        properties.setUsername(RABBIT.getAdminUsername());
        properties.setPassword(RABBIT.getAdminPassword());
        properties.setVirtualHost("/");
        return properties;
    }

    @Test
    void rabbitMQPropertiesConnectsToContainerBroker() throws Exception {
        RabbitMQProperties properties = brokerProperties();

        try (Connection connection = properties.connectionFactory().newConnection();
             Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(EXCHANGE, "direct", true);
            channel.queueDeclare(QUEUE, true, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, "");

            String msgId = "IT-RABBIT-" + System.nanoTime();
            String tag = "verify-tag";
            String tenantId = "tenant-it";
            Map<String, Object> headers = new HashMap<>();
            headers.put("ddd4j-message-id", msgId);
            headers.put("ddd4j-message-tag", tag);
            headers.put("ddd4j-tenant-id", tenantId);

            AMQP.BasicProperties sent = new AMQP.BasicProperties.Builder()
                    .messageId(msgId)
                    .contentType("application/json")
                    .headers(headers)
                    .build();
            byte[] body = ("{\"topic\":\"" + EXCHANGE + "\",\"msgId\":\"" + msgId + "\"}")
                    .getBytes(StandardCharsets.UTF_8);

            channel.basicPublish(EXCHANGE, "", sent, body);
            GetResponse received = channel.basicGet(QUEUE, true);
            assertNotNull(received, "消息应能从真实 RabbitMQ broker 消费");

            // 验证 ddd4j 消息头（带 . 与 -）与 broker AMQP 协议兼容
            assertEquals(msgId, received.getProps().getMessageId(),
                    "messageId 完整保留");
            Map<String, Object> gotHeaders = received.getProps().getHeaders();
            assertEquals(msgId, gotHeaders.get("ddd4j-message-id").toString());
            assertEquals(tag, gotHeaders.get("ddd4j-message-tag").toString());
            assertEquals(tenantId, gotHeaders.get("ddd4j-tenant-id").toString());
            assertEquals(body.length, received.getBody().length,
                    "消息体长度一致（未被截断）");
        }
    }

    @Test
    void rabbitMQContainerExposesStandardAmqpPort() throws Exception {
        assertNotNull(RABBIT.getAmqpPort(), "AMQP 端口应就绪");
        assertNotNull(RABBIT.getHost(), "AMQP 主机应就绪");
        // 验证容器确实启动（默认端口 5672 或映射到随机端口）
        assertTrue(RABBIT.getAmqpPort() > 0);
    }

    @Test
    void clientShouldRoundTripCausalityThroughNativeAmqpHeaders() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String exchange = "ddd4j.it.rabbit.causality." + suffix;
        String queue = "ddd4j.it.rabbit.causality.raw." + suffix;
        String group = "ddd4j.it.causality." + suffix;
        String listenerQueue = group + ".native.RetryingListener.handle";
        RabbitMQProperties properties = brokerProperties();
        properties.setExchange(exchange);
        properties.setProducerChannelPoolSize(1);
        properties.setAutoAck(false);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<MQEvent> consumed = new AtomicReference<>();
        AtomicReference<Acknowledgment> acknowledgment = new AtomicReference<>();

        try (Connection connection = properties.connectionFactory().newConnection();
             Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, "direct", false);
            channel.queueDeclare(queue, false, false, false, null);
            channel.queueBind(queue, exchange, "native.causality");
            RabbitMQClient client = new RabbitMQClient(connection) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }

                @Override
                public void consume(MQListener listener, MQEvent event, Acknowledgment ack) {
                    consumed.set(event);
                    acknowledgment.set(ack);
                    delivered.countDown();
                }
            };
            try {
                java.util.function.Consumer<MQEvent> producer = client.initProducer(properties);
                for (int i = 0; i < 3; i++) {
                    MQEvent event = new MQEvent();
                    event.setMsgId("causality-" + suffix + "-" + i);
                    event.setNamespace("native");
                    event.setTopic("causality");
                    if (i >= 1) {
                        event.setCorrelationId("correlation-" + suffix);
                    }
                    if (i == 2) {
                        event.setCausationId("cause-" + suffix);
                    }
                    producer.accept(event);
                    GetResponse raw = channel.basicGet(queue, true);
                    assertNotNull(raw);
                    Map<String, Object> headers = raw.getProps().getHeaders();
                    assertEquals(event.getMsgId(), headers.get(MQDeliveryHeaders.MESSAGE_ID).toString());
                    assertEquals(event.getCorrelationId(), headers.containsKey(MQDeliveryHeaders.CORRELATION_ID)
                            ? headers.get(MQDeliveryHeaders.CORRELATION_ID).toString() : null);
                    assertEquals(event.getCausationId(), headers.containsKey(MQDeliveryHeaders.CAUSATION_ID)
                            ? headers.get(MQDeliveryHeaders.CAUSATION_ID).toString() : null);
                }

                java.lang.reflect.Method method = RetryingListener.class.getMethod("handle", MQEvent.class);
                MQListener listener = new MQListener(new RetryingListener(), method, group, "native", "causality",
                        null, Collections.singletonList("*"), ".");
                channel.queueDeclare(listenerQueue, properties.isDurable(), false, false, null);
                channel.queueBind(listenerQueue, exchange, "native.causality");
                assertTrue(client.initConsumer(listener, properties));
                MQEvent payloadEvent = new MQEvent();
                payloadEvent.setMsgId("payload-id");
                payloadEvent.setNamespace("native");
                payloadEvent.setTopic("causality");
                Map<String, Object> nativeHeaders = new HashMap<>();
                nativeHeaders.put(MQDeliveryHeaders.MESSAGE_ID, "native-id");
                nativeHeaders.put(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
                nativeHeaders.put(MQDeliveryHeaders.CAUSATION_ID, "native-cause");
                AMQP.BasicProperties nativeProperties = new AMQP.BasicProperties.Builder()
                        .messageId("amqp-id").headers(nativeHeaders).build();
                String payload = new JsonMQEventSerialization().serialize(payloadEvent);
                channel.basicPublish(exchange, "native.causality", nativeProperties,
                        payload.getBytes(StandardCharsets.UTF_8));
                assertTrue(delivered.await(10, TimeUnit.SECONDS));
                assertEquals("native-id", consumed.get().getMsgId());
                assertEquals("native-correlation", consumed.get().getCorrelationId());
                assertEquals("native-cause", consumed.get().getCausationId());
                assertEquals("native-correlation", acknowledgment.get().correlationId());
            } finally {
                client.close();
                channel.queueDelete(listenerQueue);
                channel.queueDelete(queue);
            }
        }
    }

    @Test
    void producerMustReportClosedConnectionInsteadOfReturningSuccess() throws Exception {
        RabbitMQProperties properties = brokerProperties();
        properties.setExchange(EXCHANGE);
        Connection connection = properties.connectionFactory().newConnection();
        try {
            RabbitMQClient client = new RabbitMQClient(connection) {
                /**
                 * serialization() 方法。
                 * @return 方法执行结果。
                 */
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new io.ddd4j.mq.serialization.JsonMQEventSerialization();
                }
            };
            java.util.function.Consumer<io.ddd4j.mq.event.MQEvent> producer = client.initProducer(properties);
            io.ddd4j.mq.event.MQEvent event = new io.ddd4j.mq.event.MQEvent();
            event.setMsgId("closed-connection-message");
            event.setTopic("closed-connection");
            connection.close();

            IllegalStateException failure = org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class, () -> producer.accept(event));
            assertTrue(failure.getCause() instanceof com.rabbitmq.client.ShutdownSignalException);
        } finally {
            if (connection.isOpen()) {
                connection.close();
            }
        }
    }

    @Test
    void failedListenerShouldBeNackedAndRedeliveredByTheBroker() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String exchange = "ddd4j.it.rabbit.retry." + suffix;
        String group = "ddd4j.it." + suffix;
        String queue = group + ".native.RetryingListener.handle";
        String routingKey = "native.retry";
        RabbitMQProperties properties = brokerProperties();
        properties.setExchange(exchange);
        properties.setAutoAck(false);
        RetryingListener bean = new RetryingListener();
        java.lang.reflect.Method method = RetryingListener.class.getMethod("handle", MQEvent.class);
        method.setAccessible(true);
        MQListener listener = new MQListener(bean, method, group, "native", "retry", null,
                Collections.singletonList("*"), ".");

        try (Connection connection = properties.connectionFactory().newConnection();
             Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, "direct", false);
            channel.queueDeclare(queue, properties.isDurable(), false, false, null);
            channel.queueBind(queue, exchange, routingKey);
            RabbitMQClient client = new RabbitMQClient(connection) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            try {
                assertTrue(client.initConsumer(listener, properties));
                MQEvent event = new MQEvent();
                event.setMsgId("retry-" + suffix);
                event.setNamespace("native");
                event.setTopic("retry");
                String payload = new JsonMQEventSerialization().serialize(event);
                assertEquals(event.getMsgId(), new JsonMQEventSerialization()
                        .<String, MQEvent>deserialize(payload, MQEvent.class).getMsgId());
                AMQP.BasicProperties headers = new AMQP.BasicProperties.Builder()
                        .messageId(event.getMsgId()).build();
                channel.basicPublish(exchange, routingKey, headers, payload.getBytes(StandardCharsets.UTF_8));

                assertTrue(bean.redelivered.await(10, TimeUnit.SECONDS),
                        "failed delivery should be requeued; attempts=" + bean.attempts.get()
                                + ", ready=" + channel.messageCount(queue));
                assertEquals(2, bean.attempts.get());
                assertEquals(event.getMsgId(), bean.lastMessageId);
            } finally {
                client.close();
            }
        }
    }

    @Test
    void nativeAcknowledgmentShouldRequeueThenPermanentlyConfirm() throws Exception {
        String queue = "ddd4j.it.rabbit.ack." + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = brokerProperties().connectionFactory().newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(queue, false, false, false, null);
            channel.basicPublish("", queue, null, "ack-payload".getBytes(StandardCharsets.UTF_8));

            GetResponse first = channel.basicGet(queue, false);
            assertNotNull(first);
            RabbitAcknowledgment retry = new RabbitAcknowledgment(channel,
                    first.getEnvelope().getDeliveryTag(), "ack-message", null);
            retry.nack(true);
            assertTrue(retry.isAcknowledged());

            GetResponse second = channel.basicGet(queue, false);
            assertNotNull(second, "nack(requeue=true) should return the message to RabbitMQ");
            assertTrue(second.getEnvelope().isRedeliver());
            RabbitAcknowledgment confirmation = new RabbitAcknowledgment(channel,
                    second.getEnvelope().getDeliveryTag(), "ack-message", null);
            confirmation.ack();
            assertTrue(confirmation.isAcknowledged());

            try (Channel verification = connection.createChannel()) {
                assertNull(verification.basicGet(queue, true), "ack should remove the delivery from the queue");
                verification.queueDelete(queue);
            }
        }
    }

    @Test
    void requiredListenerDeclarationFailureShouldRollBackOwnedChannels() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String group = "ddd4j.it.rollback." + suffix;
        String queue = group + ".native.RetryingListener.handle";
        RabbitMQProperties properties = brokerProperties();
        properties.setEnabled(true);
        properties.setBroker("rabbit");
        properties.setProducerChannelPoolSize(2);
        properties.setExchange("ddd4j.it.rabbit.rollback." + suffix);
        java.lang.reflect.Method method = RetryingListener.class.getMethod("handle", MQEvent.class);
        method.setAccessible(true);
        MQListener listener = new MQListener(new RetryingListener(), method, group, "native", "rollback",
                null, Collections.singletonList("*"), ".");

        try (Connection connection = properties.connectionFactory().newConnection();
             Channel setup = connection.createChannel()) {
            setup.queueDeclare(queue, false, false, false, null);
            RabbitMQClient client = new RabbitMQClient(connection);
            try {
                assertThrows(MQInitializationException.class,
                        () -> client.init(Collections.singletonList(listener), properties,
                                new JsonMQEventSerialization(), null));
                assertEquals(MQStartupState.FAILED, client.startupStatus().snapshot().state());
                assertEquals(0, client.lifecycle().checkpoint(), "all owned producer and consumer channels rolled back");
                assertTrue(connection.isOpen(), "the injected connection belongs to the caller");
                assertTrue(setup.isOpen(), "caller-owned channel remains open");
            } finally {
                client.close();
                client.close();
                setup.queueDelete(queue);
            }
            assertTrue(connection.isOpen());
        }
    }

    @Test
    void optionalListenerDeclarationFailureShouldKeepProducerAvailable() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String group = "ddd4j.it.optional." + suffix;
        String queue = group + ".native.RetryingListener.handle";
        RabbitMQProperties properties = brokerProperties();
        properties.setEnabled(true);
        properties.setBroker("rabbit");
        properties.setProducerChannelPoolSize(2);
        properties.setExchange("ddd4j.it.rabbit.optional." + suffix);
        java.lang.reflect.Method method = RetryingListener.class.getMethod("handle", MQEvent.class);
        method.setAccessible(true);
        MQListener listener = new MQListener(new RetryingListener(), method, group, "native", "optional",
                null, Collections.singletonList("*"), ".");
        listener.setRequired(false);

        try (Connection connection = properties.connectionFactory().newConnection();
             Channel setup = connection.createChannel()) {
            setup.queueDeclare(queue, false, false, false, null);
            setup.exchangeDeclare(properties.getExchange(), "direct", false);
            setup.queueBind(queue, properties.getExchange(), "native.optional");
            RabbitMQClient client = new RabbitMQClient(connection);
            try {
                client.init(Collections.singletonList(listener), properties, new JsonMQEventSerialization(), null);
                assertEquals(MQStartupState.DEGRADED, client.startupStatus().snapshot().state());
                assertEquals(1, client.startupStatus().snapshot().failures().size());
                assertEquals(2, client.lifecycle().checkpoint(), "producer channels remain available");
                assertTrue(connection.isOpen());
                MQEvent event = new MQEvent();
                event.setMsgId("optional-" + suffix);
                event.setNamespace("native");
                event.setTopic("optional");
                event.broker("rabbit").publish();
                GetResponse published = setup.basicGet(queue, true);
                assertNotNull(published, "degraded startup should retain the original producer");
                assertEquals(event.getMsgId(), published.getProps().getMessageId());
            } finally {
                client.close();
                setup.queueDelete(queue);
            }
            assertEquals(0, client.lifecycle().checkpoint());
            assertTrue(connection.isOpen());
        }
    }

    @Test
    void closingDuringUnacknowledgedDeliveryShouldReleaseAndRequeue() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String group = "ddd4j.it.close." + suffix;
        String queue = group + ".native.BlockingListener.handle";
        String exchange = "ddd4j.it.rabbit.close." + suffix;
        RabbitMQProperties properties = brokerProperties();
        properties.setExchange(exchange);
        properties.setAutoAck(false);
        BlockingListener bean = new BlockingListener();
        java.lang.reflect.Method method = BlockingListener.class.getMethod("handle", MQEvent.class);
        method.setAccessible(true);
        MQListener listener = new MQListener(bean, method, group, "native", "close", null,
                Collections.singletonList("*"), ".");
        ExecutorService closer = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "rabbit-close-it");
            thread.setDaemon(true);
            return thread;
        });

        try (Connection connection = properties.connectionFactory().newConnection();
             Channel setup = connection.createChannel()) {
            setup.exchangeDeclare(exchange, "direct", false);
            setup.queueDeclare(queue, properties.isDurable(), false, false, null);
            setup.queueBind(queue, exchange, "native.close");
            RabbitMQClient client = new RabbitMQClient(connection) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            try {
                assertTrue(client.initConsumer(listener, properties));
                MQEvent event = new MQEvent();
                event.setMsgId("close-" + suffix);
                event.setNamespace("native");
                event.setTopic("close");
                String payload = new JsonMQEventSerialization().serialize(event);
                setup.basicPublish(exchange, "native.close", new AMQP.BasicProperties.Builder()
                        .messageId(event.getMsgId()).build(), payload.getBytes(StandardCharsets.UTF_8));
                assertTrue(bean.entered.await(5, TimeUnit.SECONDS), "consumer should enter before close");

                Future<?> closed = closer.submit(client::close);
                closed.get(5, TimeUnit.SECONDS);
                assertTrue(connection.isOpen(), "injected connection must remain caller-owned");
                assertEquals(0, client.lifecycle().checkpoint());
                assertEquals(MQStartupState.STOPPED, client.startupStatus().snapshot().state());
                GetResponse redelivered = setup.basicGet(queue, false);
                assertNotNull(redelivered, "closing the consumer channel must requeue its unacked message");
                assertTrue(redelivered.getEnvelope().isRedeliver());
                setup.basicAck(redelivered.getEnvelope().getDeliveryTag(), false);
                assertEquals(1, bean.attempts.get());
            } finally {
                bean.release.countDown();
                client.close();
                closer.shutdownNow();
                setup.queueDelete(queue);
            }
        } finally {
            closer.shutdownNow();
        }
    }

    public static final class RetryingListener {
        private final AtomicInteger attempts = new AtomicInteger();
        private final CountDownLatch redelivered = new CountDownLatch(1);
        private volatile String lastMessageId;

        public void handle(MQEvent event) {
            lastMessageId = event.getMsgId();
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("retry once");
            }
            redelivered.countDown();
        }
    }

    public static final class BlockingListener {
        private final AtomicInteger attempts = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        public void handle(MQEvent event) throws InterruptedException {
            attempts.incrementAndGet();
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("close test listener was not released");
            }
        }
    }
}
