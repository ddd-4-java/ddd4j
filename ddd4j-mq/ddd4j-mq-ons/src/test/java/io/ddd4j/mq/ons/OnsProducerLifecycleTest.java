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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 验证 <code>OnsProducerLifecycle</code> 在 <code>closedAdapter</code>、<code>producerHandle</code> 等场景中的行为。
 */
class OnsProducerLifecycleTest {

    /**
     * 验证<code>client.initProducer(new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getCause()</code> 与…为同一实例 <code>startupFailure</code>。
     */
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

    /**
     * 验证<code>client.initProducer(new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void failedBorrowedProducerStartDoesNotShutDownProducer() {
        Producer nativeProducer = mock(Producer.class);
        doThrow(new IllegalStateException("startup failed")).when(nativeProducer).start();
        OnsMQClient client = new OnsMQClient(nativeProducer, new OnsProperties());

        assertThrows(IllegalStateException.class, () -> client.initProducer(new MQProperties()));
        client.close();
        verify(nativeProducer, never()).shutdown();
    }

    /**
     * 验证<code>client.initConsumer(listener, new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     */
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

    /**
     * 验证<code>client.initConsumer(listener, new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     */
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

    /**
     * 在测试框架中执行 <code>mock()</code>、<code>client.initProducer()</code>、<code>client.close()</code>，验证该场景。
     */
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

    /**
     * 验证<code>client.initProducer(new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     */
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

    /**
     * 验证<code>client.initConsumer(listener, new MQProperties())</code> 抛出 <code>IllegalStateException.class</code>。
     */
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

    /**
     * 验证<code>publisher.accept(new MQEvent())</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage()</code> 等于 <code>"ONS client is closed"</code>。
     */
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
