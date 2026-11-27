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
package io.ddd4j.mq.pulsar;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 Pulsar Broker 验证带 tag 的事件能到达同一逻辑 topic 的订阅者。 */
@Testcontainers(disabledWithoutDocker = true)
class PulsarContainerIntegrationTest {

    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb Pulsar \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final PulsarContainer PULSAR = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.2.4"));

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * \u9a8c\u8bc1<code>delivered.await(30, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>received.get().getMsgId()</code> \u7b49\u4e8e <code>event.getMsgId()</code>\uff0c<code>acknowledgment.get().correlationId()</code> \u7b49\u4e8e <code>event.getCorrelationId()</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void taggedEventReachesSubscriberWithNativeCausalityProperties() throws Exception {
        PulsarProperties properties = new PulsarProperties();
        properties.setServiceUrl(PULSAR.getPulsarBrokerUrl());
        properties.setNamespace("default");
        properties.setEnabled(true);
        properties.setBroker("pulsar");
        properties.setSubscriptionName("ddd4j-it-" + UUID.randomUUID());
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<MQEvent> received = new AtomicReference<>();
        AtomicReference<Acknowledgment> acknowledgment = new AtomicReference<>();
        PulsarClient nativeClient = properties.client();
        try {
            PulsarMQClient adapter = new PulsarMQClient(nativeClient, properties) {
                @Override
                public void consume(MQListener listener, MQEvent event, Acknowledgment ack) {
                    received.set(event);
                    acknowledgment.set(ack);
                    delivered.countDown();
                }
            };
            try {
                MQListener listener = MQListener.builder().group("native").topic("orders")
                        .tags("priority").build();
                adapter.init(Collections.singletonList(listener), properties, new MQEventSerialization() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T serialize(Object event) {
                        return (T) "{}";
                    }

                    @Override
                    public <S, T> T deserialize(S value, Class<T> type) {
                        return type.cast(new MQEvent());
                    }
                }, null);
                MQEvent event = new MQEvent();
                event.setTopic("orders");
                event.setTag("priority");
                event.setMsgId("stable-" + UUID.randomUUID());
                event.setCorrelationId("correlation-" + UUID.randomUUID());
                event.setCausationId("causation-" + UUID.randomUUID());
                event.publish();

                assertTrue(delivered.await(30, TimeUnit.SECONDS), "tagged event must reach the subscribed topic");
                assertEquals(event.getMsgId(), received.get().getMsgId());
                assertEquals(event.getTag(), received.get().getTag());
                assertEquals(event.getCorrelationId(), received.get().getCorrelationId());
                assertEquals(event.getCausationId(), received.get().getCausationId());
                assertEquals(event.getCorrelationId(), acknowledgment.get().correlationId());
            } finally {
                adapter.close();
            }
        } finally {
            nativeClient.close();
        }
    }

    /**
     * \u9a8c\u8bc1<code>retried.await(30, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>attempts.get()</code> \u7b49\u4e8e <code>2</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void failedListenerIsRedeliveredByNativeBroker() throws Exception {
        PulsarProperties properties = new PulsarProperties();
        properties.setServiceUrl(PULSAR.getPulsarBrokerUrl());
        properties.setNamespace("default");
        properties.setEnabled(true);
        properties.setBroker("pulsar");
        properties.setSubscriptionName("ddd4j-retry-" + UUID.randomUUID());
        properties.setNegativeAckRedeliveryDelayMs(500L);
        CountDownLatch retried = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        PulsarClient nativeClient = properties.client();
        try {
            PulsarMQClient adapter = new PulsarMQClient(nativeClient, properties) {
                @Override
                public void consume(MQListener listener, MQEvent event, Acknowledgment ack) {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("first delivery fails");
                    }
                    retried.countDown();
                }
            };
            try {
                MQListener listener = MQListener.builder().group("native").topic("retry")
                        .tags("priority").build();
                adapter.init(Collections.singletonList(listener), properties, new MQEventSerialization() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T serialize(Object event) {
                        return (T) "{}";
                    }

                    @Override
                    public <S, T> T deserialize(S value, Class<T> type) {
                        return type.cast(new MQEvent());
                    }
                }, null);
                MQEvent event = new MQEvent();
                event.setTopic("retry");
                event.setTag("priority");
                event.setMsgId("retry-" + UUID.randomUUID());
                event.publish();

                assertTrue(retried.await(30, TimeUnit.SECONDS), "failed delivery must be redelivered");
                assertEquals(2, attempts.get());
            } finally {
                adapter.close();
            }
        } finally {
            nativeClient.close();
        }
    }
}
