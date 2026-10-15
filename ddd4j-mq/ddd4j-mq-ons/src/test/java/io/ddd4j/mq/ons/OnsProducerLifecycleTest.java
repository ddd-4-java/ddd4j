/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OnsProducerLifecycleTest {

    @Test
    void repeatedInitializationStartsInjectedProducerOnce() {
        Producer producer = mock(Producer.class);
        OnsMQClient client = new OnsMQClient(producer, new OnsProperties());

        client.initProducer(new MQProperties());
        client.initProducer(new MQProperties());
        client.close();

        verify(producer, times(1)).start();
        verify(producer, never()).shutdown();
    }

    @Test
    void closedAdapterRejectsProducerInitialization() {
        Producer producer = mock(Producer.class);
        OnsMQClient client = new OnsMQClient(producer, new OnsProperties());
        client.initProducer(new MQProperties());
        client.close();

        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
        verify(producer, times(1)).start();
        verify(producer, never()).shutdown();
    }

    @Test
    void closedAdapterRejectsConsumerBeforeCreatingBroker() {
        OnsProperties properties = new OnsProperties() {
            @Override
            public Properties sessionProperties(String groupName) {
                throw new AssertionError("Closed adapter reached ONS broker creation");
            }
        };
        MQListener listener = MQListener.builder().group("group").topic("topic").tags("*").build();
        OnsMQClient client = new OnsMQClient(properties);
        client.close();

        assertThrows(IllegalStateException.class, () -> client.initConsumer(listener, new MQProperties()));
    }

    @Test
    void producerHandleRejectsPublishAfterClose() {
        Producer producer = mock(Producer.class);
        OnsMQClient client = new OnsMQClient(producer, new OnsProperties());
        Consumer<MQEvent> publisher = client.initProducer(new MQProperties());
        client.close();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> publisher.accept(new MQEvent()));
        assertEquals("ONS client is closed", failure.getMessage());
    }
}
