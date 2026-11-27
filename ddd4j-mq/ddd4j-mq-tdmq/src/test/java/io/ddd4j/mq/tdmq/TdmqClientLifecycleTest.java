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

/**
 * 验证 <code>TdmqClientLifecycle</code> 在 <code>closedAdapter</code>、<code>producerHandle</code> 等场景中的行为。
 */
class TdmqClientLifecycleTest {

    /**
     * 验证<code>client.initProducer(new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void closedAdapterRejectsProducerInitialization() {
        TdmqMQClient client = new TdmqMQClient(new TdmqProperties());
        client.close();

        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>subscriptions.get()</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>publisher.accept(new MQEvent())</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage()</code> 等于 <code>"TDMQ client is closed"</code>。
     */
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
