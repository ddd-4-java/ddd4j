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
package io.ddd4j.mq.sqs;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 SQS 兼容协议端点验证适配器发布、消费与确认路径。
 */
@Testcontainers(disabledWithoutDocker = true)
class SqsBrokerIntegrationTest {

    @Container
    static final GenericContainer<?> SQS = new GenericContainer<>(
            DockerImageName.parse("softwaremill/elasticmq:1.6.16"))
            .withExposedPorts(9324)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    @Test
    void publishesConsumesAndDeletesMessageThroughSqsProtocol() throws Exception {
        SqsProperties properties = new SqsProperties();
        properties.setEndpointOverride("http://" + SQS.getHost() + ":" + SQS.getMappedPort(9324));
        properties.setAccessKey("test");
        properties.setSecretKey("test");
        properties.setWaitTimeSeconds(0);
        properties.setPollIntervalMs(50L);

        try (SqsClient nativeClient = properties.client()) {
            String queueUrl = nativeClient.createQueue(r -> r.queueName("ddd4j-it-" + System.nanoTime()))
                    .queueUrl();
            RecordingListener receiver = new RecordingListener();
            MQListener listener = new MQListener(receiver,
                    RecordingListener.class.getMethod("handle", MQEvent.class),
                    "ddd4j-sqs-it", "ddd4j", queueUrl, "*",
                    Collections.singletonList("*"), ".");
            SqsMQClient adapter = new SqsMQClient(properties) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            try {
                assertTrue(adapter.initConsumer(listener, properties));
                MQEvent event = new MQEvent();
                event.setMsgId("sqs-protocol-message");
                event.setNamespace("ddd4j");
                event.setTopic(queueUrl);
                event.setCorrelationId("sqs-correlation");
                adapter.initProducer(properties).accept(event);

                assertTrue(receiver.received.await(10, TimeUnit.SECONDS));
                assertEquals(event.getMsgId(), receiver.messageId);
                assertEquals(event.getCorrelationId(), receiver.correlationId);
                awaitQueueEmpty(nativeClient, queueUrl);
            } finally {
                adapter.close();
            }
        }
    }

    @Test
    void redeliversAfterListenerFailureAndDeletesAfterSuccess() throws Exception {
        SqsProperties properties = new SqsProperties();
        properties.setEndpointOverride("http://" + SQS.getHost() + ":" + SQS.getMappedPort(9324));
        properties.setAccessKey("test");
        properties.setSecretKey("test");
        properties.setWaitTimeSeconds(0);
        properties.setPollIntervalMs(50L);

        try (SqsClient nativeClient = properties.client()) {
            String queueUrl = nativeClient.createQueue(r -> r.queueName("ddd4j-retry-" + System.nanoTime()))
                    .queueUrl();
            FailOnceListener receiver = new FailOnceListener();
            MQListener listener = new MQListener(receiver,
                    FailOnceListener.class.getMethod("handle", MQEvent.class),
                    "ddd4j-sqs-retry", "ddd4j", queueUrl, "*",
                    Collections.singletonList("*"), ".");
            SqsMQClient adapter = new SqsMQClient(properties) {
                @Override
                public io.ddd4j.mq.event.MQEventSerialization serialization() {
                    return new JsonMQEventSerialization();
                }
            };
            try {
                assertTrue(adapter.initConsumer(listener, properties));
                MQEvent event = new MQEvent();
                event.setMsgId("sqs-retry-message");
                event.setNamespace("ddd4j");
                event.setTopic(queueUrl);
                event.setCorrelationId("sqs-retry-correlation");
                adapter.initProducer(properties).accept(event);

                assertTrue(receiver.receivedTwice.await(10, TimeUnit.SECONDS));
                assertEquals(2, receiver.attempts.get());
                assertEquals(event.getMsgId(), receiver.messageId);
                assertEquals(event.getCorrelationId(), receiver.correlationId);
                awaitQueueEmpty(nativeClient, queueUrl);
            } finally {
                adapter.close();
            }
        }
    }

    private static void awaitQueueEmpty(SqsClient nativeClient, String queueUrl) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        int outstanding;
        do {
            Map<QueueAttributeName, String> counts = nativeClient.getQueueAttributes(r ->
                            r.queueUrl(queueUrl).attributeNames(
                                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
                    .attributes();
            outstanding = Integer.parseInt(counts.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                    + Integer.parseInt(counts.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
            if (outstanding == 0) {
                break;
            }
            Thread.sleep(50L);
        } while (System.nanoTime() < deadline);
        assertEquals(0, outstanding);
    }

    /** 记录实际消费到的消息值。 */
    public static final class RecordingListener {
        private final CountDownLatch received = new CountDownLatch(1);
        private volatile String messageId;
        private volatile String correlationId;

        /** @param event 实际消费的消息 */
        public void handle(MQEvent event) {
            messageId = event.getMsgId();
            correlationId = event.getCorrelationId();
            received.countDown();
        }
    }

    /** 第一次消费失败，第二次记录实际重投。 */
    public static final class FailOnceListener {
        private final AtomicInteger attempts = new AtomicInteger();
        private final CountDownLatch receivedTwice = new CountDownLatch(1);
        private volatile String messageId;
        private volatile String correlationId;

        /** @param event 实际消费的消息 */
        public void handle(MQEvent event) {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("first delivery fails");
            }
            messageId = event.getMsgId();
            correlationId = event.getCorrelationId();
            receivedTwice.countDown();
        }
    }
}
