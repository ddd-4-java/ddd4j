/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.tdmq;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TdmqClientLifecycleTest {

    @Test
    void closedAdapterRejectsProducerInitialization() {
        TdmqMQClient client = new TdmqMQClient(new TdmqProperties());
        client.close();

        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
    }

    @Test
    void closedAdapterRejectsConsumerWithoutSubscribing() {
        AtomicInteger subscriptions = new AtomicInteger();
        TdmqMQClient client = new TdmqMQClient(new TdmqProperties());
        client.setBrokerSubscriber((topic, tags, group, handler) -> {
            subscriptions.incrementAndGet();
            return () -> {
            };
        });
        client.close();

        assertThrows(IllegalStateException.class, () -> client.initConsumer(
                MQListener.builder().group("group").topic("topic").tags("*").build(),
                new MQProperties()));
        assertEquals(0, subscriptions.get());
    }

    @Test
    void producerHandleRejectsPublishAfterClose() {
        TdmqMQClient client = new TdmqMQClient(new TdmqProperties());
        Consumer<MQEvent> publisher = client.initProducer(new MQProperties());
        client.close();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> publisher.accept(new MQEvent()));
        assertEquals("TDMQ client is closed", failure.getMessage());
    }
}
