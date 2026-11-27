/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.sqs;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 <code>SqsClientLifecycle</code> \u5728 <code>consumer</code>\u3001<code>requiredListenerRollbackAllowsSameAdapterToRestart</code>\u3001<code>closedAdapter</code>\u3001<code>invalidPollInterval</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class SqsClientLifecycleTest {

    /**
     * \u9a8c\u8bc1<code>adapter.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>polled.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void consumerWithoutProducerCreatesAndClosesOwnedClient() throws Exception {
        SqsClient nativeClient = mock(SqsClient.class);
        CountDownLatch polled = new CountDownLatch(1);
        when(nativeClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            polled.countDown();
            return ReceiveMessageResponse.builder().build();
        });
        SqsProperties properties = new SqsProperties() {
            @Override
            public SqsClient client() {
                return nativeClient;
            }
        };
        properties.setPollIntervalMs(1000L);
        MQListener listener = new MQListener();
        listener.setTopic("https://sqs.example/queue");
        SqsMQClient adapter = new SqsMQClient(properties);

        try {
            assertTrue(adapter.initConsumer(listener, properties));
            assertTrue(polled.await(5, TimeUnit.SECONDS));
        } finally {
            adapter.close();
        }

        verify(nativeClient, atLeastOnce()).receiveMessage(any(ReceiveMessageRequest.class));
        verify(nativeClient, times(1)).close();
    }

    /** \u9a8c\u8bc1<code>adapter.initConsumer(listener, properties)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>creations.get()</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void closedAdapterRejectsNewConsumerWithoutCreatingClient() {
        SqsClient nativeClient = mock(SqsClient.class);
        AtomicInteger creations = new AtomicInteger();
        SqsProperties properties = new SqsProperties() {
            @Override
            public SqsClient client() {
                creations.incrementAndGet();
                return nativeClient;
            }
        };
        MQListener listener = new MQListener();
        listener.setTopic("https://sqs.example/queue");
        SqsMQClient adapter = new SqsMQClient(properties);
        adapter.close();

        assertThrows(IllegalStateException.class, () -> adapter.initConsumer(listener, properties));
        assertEquals(0, creations.get());
        verify(nativeClient, never()).close();
    }

    /** \u9a8c\u8bc1<code>adapter.initConsumer(listener, properties)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>creations.get()</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void invalidPollIntervalRejectsConsumerBeforeCreatingClient() {
        AtomicInteger creations = new AtomicInteger();
        SqsProperties properties = new SqsProperties() {
            @Override
            public SqsClient client() {
                creations.incrementAndGet();
                return mock(SqsClient.class);
            }
        };
        properties.setPollIntervalMs(0L);
        MQListener listener = new MQListener();
        listener.setTopic("https://sqs.example/queue");
        SqsMQClient adapter = new SqsMQClient(properties);

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.initConsumer(listener, properties));
            assertEquals(0, creations.get());
        } finally {
            adapter.close();
        }
    }

    /** \u9a8c\u8bc1<code>publisher.accept(new MQEvent())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"SQS client is closed"</code>\u3002 */
    @Test
    void producerHandleRejectsPublishAfterClose() {
        SqsClient nativeClient = mock(SqsClient.class);
        SqsMQClient adapter = new SqsMQClient(nativeClient);
        Consumer<MQEvent> publisher = adapter.initProducer(new SqsProperties());
        adapter.close();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> publisher.accept(new MQEvent()));
        assertEquals("SQS client is closed", failure.getMessage());
    }

    /**
     * \u9a8c\u8bc1<code>releaseSend.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>closing.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>close.get(150, TimeUnit.MILLISECONDS)</code> \u629b\u51fa <code>TimeoutException.class</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void closeWaitsForAnInFlightSendBeforeClosingOwnedClient() throws Exception {
        SqsClient nativeClient = mock(SqsClient.class);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        when(nativeClient.sendMessage(any(SendMessageRequest.class))).thenAnswer(invocation -> {
            sending.countDown();
            assertTrue(releaseSend.await(5, TimeUnit.SECONDS));
            return SendMessageResponse.builder().messageId("native-1").build();
        });
        SqsProperties properties = new SqsProperties() {
            @Override
            public SqsClient client() {
                return nativeClient;
            }
        };
        SqsMQClient adapter = new SqsMQClient(properties);
        Consumer<MQEvent> publisher = adapter.initProducer(properties);
        MQEvent event = new MQEvent();
        event.setTopic("https://sqs.example/queue");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        MQEventSerialization previousSerialization = BaseContext.get(MQClient.MQ_SERIALIZATION);
        BaseContext.inject(MQClient.MQ_SERIALIZATION, new MQEventSerialization() {
            @Override
            public <S, T> T deserialize(S src, Class<T> dist) {
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object src) {
                return (T) "{}";
            }
        });

        try {
            Future<?> publish = executor.submit(() -> publisher.accept(event));
            if (!sending.await(5, TimeUnit.SECONDS)) {
                publish.get(1, TimeUnit.SECONDS);
                throw new AssertionError("SQS send was not invoked");
            }
            Future<?> close = executor.submit(() -> {
                closing.countDown();
                adapter.close();
            });
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> close.get(150, TimeUnit.MILLISECONDS));
            verify(nativeClient, never()).close();

            releaseSend.countDown();
            publish.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);
            verify(nativeClient, times(1)).close();
        } finally {
            releaseSend.countDown();
            executor.shutdownNow();
            adapter.close();
            if (Objects.isNull(previousSerialization)) {
                BaseContext.remove(MQClient.MQ_SERIALIZATION);
            } else {
                BaseContext.inject(MQClient.MQ_SERIALIZATION, previousSerialization);
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>adapter.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>receiving.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>close.get(150, TimeUnit.MILLISECONDS)</code> \u629b\u51fa <code>TimeoutException.class</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void closeWaitsForAnInFlightReceiveBeforeClosingOwnedClient() throws Exception {
        SqsClient nativeClient = mock(SqsClient.class);
        CountDownLatch receiving = new CountDownLatch(1);
        CountDownLatch releaseReceive = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        when(nativeClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            receiving.countDown();
            while (releaseReceive.getCount() > 0) {
                try {
                    releaseReceive.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    // Simulate a native long poll that has not finished after interruption.
                }
            }
            return ReceiveMessageResponse.builder().build();
        });
        SqsProperties properties = new SqsProperties() {
            @Override
            public SqsClient client() {
                return nativeClient;
            }
        };
        MQListener listener = new MQListener();
        listener.setTopic("https://sqs.example/queue");
        SqsMQClient adapter = new SqsMQClient(properties);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            assertTrue(adapter.initConsumer(listener, properties));
            assertTrue(receiving.await(5, TimeUnit.SECONDS));
            Future<?> close = executor.submit(() -> {
                closing.countDown();
                adapter.close();
            });
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> close.get(150, TimeUnit.MILLISECONDS));
            verify(nativeClient, never()).close();

            releaseReceive.countDown();
            close.get(5, TimeUnit.SECONDS);
            verify(nativeClient, times(1)).close();
        } finally {
            releaseReceive.countDown();
            adapter.close();
            executor.shutdownNow();
        }
    }
}
