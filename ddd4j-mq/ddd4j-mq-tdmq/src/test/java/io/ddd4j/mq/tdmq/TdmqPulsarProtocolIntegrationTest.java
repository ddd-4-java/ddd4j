/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.tdmq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.kit.lang.MapKit;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 TDMQ 配置通过真实 Pulsar 协议跨客户端投递，并保留业务因果头。
 */
@Testcontainers
class TdmqPulsarProtocolIntegrationTest {

    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb Pulsar \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final PulsarContainer PULSAR = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.2.4"))
            .withTmpFs(MapKit.builder("/pulsar/data", "rw,mode=1777")
                    .put("/pulsar/logs", "rw,mode=1777").build());

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * \u9a8c\u8bc1<code>delivered.await(30, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>received.get().getMsgId()</code> \u7b49\u4e8e <code>event.getMsgId()</code>\uff0c<code>received.get().getCausationId()</code> \u7b49\u4e8e <code>event.getCausationId()</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void configuredBrokerDeliversBetweenIndependentClientsWithCausality() throws Exception {
        String topic = "tdmq-" + UUID.randomUUID();
        TdmqProperties properties = new TdmqProperties();
        properties.setServiceUrl(PULSAR.getPulsarBrokerUrl());
        properties.setTenant("public");
        properties.setNamespace("default");
        properties.setDefaultGroup("tdmq-it-" + UUID.randomUUID());
        properties.setEnabled(true);
        properties.setBroker("tdmq");

        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<MQEvent> received = new AtomicReference<>();
        MQListener listener = MQListener.builder().group(properties.getDefaultGroup())
                .topic(topic).tags("*").build();
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return type.cast(new MQEvent());
            }
        };

        TdmqMQClient consumer = new TdmqMQClient(properties) {
            @Override
            public void consume(MQListener activeListener, MQEvent event, Acknowledgment ack) {
                received.set(event);
                delivered.countDown();
            }
        };
        TdmqMQClient producer = new TdmqMQClient(properties);
        try {
            consumer.init(Collections.singletonList(listener), properties, serialization, null);
            producer.init(Collections.emptyList(), properties, serialization, null);
            MQEvent event = new MQEvent();
            event.setTopic(topic);
            event.setMsgId("message-" + UUID.randomUUID());
            event.setCorrelationId("correlation-" + UUID.randomUUID());
            event.setCausationId("causation-" + UUID.randomUUID());
            event.publish();

            assertTrue(delivered.await(30, TimeUnit.SECONDS),
                    "TDMQ clients with a service URL must exchange messages through the broker");
            assertEquals(event.getMsgId(), received.get().getMsgId());
            assertEquals(event.getCorrelationId(), received.get().getCorrelationId());
            assertEquals(event.getCausationId(), received.get().getCausationId());
        } finally {
            producer.close();
            consumer.close();
        }
    }

    /**
     * \u9a8c\u8bc1<code>retried.await(30, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>attempts.get()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>redeliveredId.get()</code> \u7b49\u4e8e <code>event.getMsgId()</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void failedConsumerIsRedeliveredWithStableBusinessId() throws Exception {
        String topic = "tdmq-retry-" + UUID.randomUUID();
        TdmqProperties properties = new TdmqProperties();
        properties.setServiceUrl(PULSAR.getPulsarBrokerUrl());
        properties.setTenant("public");
        properties.setNamespace("default");
        properties.setDefaultGroup("tdmq-retry-" + UUID.randomUUID());
        properties.setEnabled(true);
        properties.setBroker("tdmq");

        CountDownLatch retried = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> redeliveredId = new AtomicReference<>();
        MQListener listener = MQListener.builder().group(properties.getDefaultGroup())
                .topic(topic).tags("*").build();
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return type.cast(new MQEvent());
            }
        };

        TdmqMQClient consumer = new TdmqMQClient(properties) {
            @Override
            public void consume(MQListener activeListener, MQEvent event, Acknowledgment ack) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("first delivery fails");
                }
                redeliveredId.set(event.getMsgId());
                retried.countDown();
            }
        };
        TdmqMQClient producer = new TdmqMQClient(properties);
        try {
            consumer.init(Collections.singletonList(listener), properties, serialization, null);
            producer.init(Collections.emptyList(), properties, serialization, null);
            MQEvent event = new MQEvent();
            event.setTopic(topic);
            event.setMsgId("retry-" + UUID.randomUUID());
            event.publish();

            assertTrue(retried.await(30, TimeUnit.SECONDS),
                    "failed TDMQ delivery must be redelivered by the broker");
            assertEquals(2, attempts.get());
            assertEquals(event.getMsgId(), redeliveredId.get());
        } finally {
            producer.close();
            consumer.close();
        }
    }

}
