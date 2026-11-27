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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** \u9a8c\u8bc1 <code>OnsProducerLifecycle</code> \u5728 <code>closedAdapter</code>\u3001<code>producerHandle</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class OnsProducerLifecycleTest {

    /** \u9a8c\u8bc1<code>client.initProducer(new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getCause()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>startupFailure</code>\u3002 */
    @Test
    void failedOwnedProducerStartShutsDownCreatedProducer() {
        Producer nativeProducer = mock(Producer.class);
        IllegalStateException startupFailure = new IllegalStateException("startup failed");
        doThrow(startupFailure).when(nativeProducer).start();
        OnsProperties properties = new OnsProperties() {
            @Override
            public Properties sessionProperties(String groupName) {
                return new Properties();
            }
        };
        OnsMQClient client = new OnsMQClient(properties) {
            @Override
            Producer createProducer(Properties sessionProperties) {
                return nativeProducer;
            }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.initProducer(new MQProperties()));
        assertSame(startupFailure, failure.getCause());
        verify(nativeProducer, times(1)).shutdown();
        client.close();
        verify(nativeProducer, times(1)).shutdown();
    }

    /** \u9a8c\u8bc1<code>client.initProducer(new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void failedBorrowedProducerStartDoesNotShutDownProducer() {
        Producer nativeProducer = mock(Producer.class);
        doThrow(new IllegalStateException("startup failed")).when(nativeProducer).start();
        OnsMQClient client = new OnsMQClient(nativeProducer, new OnsProperties());

        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
        client.close();
        verify(nativeProducer, never()).shutdown();
    }

    /** \u9a8c\u8bc1<code>client.initConsumer(listener, new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void failedConsumerSubscriptionShutsDownCreatedConsumer() {
        com.aliyun.openservices.ons.api.Consumer nativeConsumer = mock(com.aliyun.openservices.ons.api.Consumer.class);
        doThrow(new IllegalStateException("subscription failed")).when(nativeConsumer)
                .subscribe(anyString(), anyString(), any());
        OnsProperties properties = new OnsProperties() {
            @Override
            public Properties sessionProperties(String groupName) {
                return new Properties();
            }
        };
        OnsMQClient client = new OnsMQClient(properties) {
            @Override
            com.aliyun.openservices.ons.api.Consumer createConsumer(Properties sessionProperties) {
                return nativeConsumer;
            }
        };
        MQListener listener = MQListener.builder().group("group").topic("topic").tags("*").build();

        assertThrows(IllegalStateException.class, () -> client.initConsumer(listener, new MQProperties()));
        verify(nativeConsumer, times(1)).shutdown();
        client.close();
        verify(nativeConsumer, times(1)).shutdown();
    }

    /** \u9a8c\u8bc1<code>client.initConsumer(listener, new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void failedConsumerStartShutsDownCreatedConsumer() {
        com.aliyun.openservices.ons.api.Consumer nativeConsumer = mock(com.aliyun.openservices.ons.api.Consumer.class);
        doThrow(new IllegalStateException("startup failed")).when(nativeConsumer).start();
        OnsProperties properties = new OnsProperties() {
            @Override
            public Properties sessionProperties(String groupName) {
                return new Properties();
            }
        };
        OnsMQClient client = new OnsMQClient(properties) {
            @Override
            com.aliyun.openservices.ons.api.Consumer createConsumer(Properties sessionProperties) {
                return nativeConsumer;
            }
        };
        MQListener listener = MQListener.builder().group("group").topic("topic").tags("*").build();

        assertThrows(IllegalStateException.class, () -> client.initConsumer(listener, new MQProperties()));
        verify(nativeConsumer, times(1)).shutdown();
        client.close();
        verify(nativeConsumer, times(1)).shutdown();
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>client.initProducer()</code>\u3001<code>client.close()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>client.initProducer(new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>client.initConsumer(listener, new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>publisher.accept(new MQEvent())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"ONS client is closed"</code>\u3002 */
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
