/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.tdmq;

import io.ddd4j.core.context.BaseContext;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 TDMQ 配置通过真实 Pulsar 协议跨客户端投递，并保留业务因果头。 */
@Testcontainers
class TdmqPulsarProtocolIntegrationTest {

    @Container
    static final PulsarContainer PULSAR = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:2.10.6"));

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

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

            assertTrue(delivered.await(10, TimeUnit.SECONDS),
                    "TDMQ clients with a service URL must exchange messages through the broker");
            assertEquals(event.getMsgId(), received.get().getMsgId());
            assertEquals(event.getCorrelationId(), received.get().getCorrelationId());
            assertEquals(event.getCausationId(), received.get().getCausationId());
        } finally {
            producer.close();
            consumer.close();
        }
    }

}
