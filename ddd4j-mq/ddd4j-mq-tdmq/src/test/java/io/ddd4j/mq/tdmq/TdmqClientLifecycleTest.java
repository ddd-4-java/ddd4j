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

/** \u9a8c\u8bc1 <code>TdmqClientLifecycle</code> \u5728 <code>closedAdapter</code>\u3001<code>producerHandle</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class TdmqClientLifecycleTest {

    /** \u9a8c\u8bc1<code>client.initProducer(new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void closedAdapterRejectsProducerInitialization() {
        TdmqMQClient client = new TdmqMQClient(new TdmqProperties());
        client.close();

        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>subscriptions.get()</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>publisher.accept(new MQEvent())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"TDMQ client is closed"</code>\u3002 */
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
