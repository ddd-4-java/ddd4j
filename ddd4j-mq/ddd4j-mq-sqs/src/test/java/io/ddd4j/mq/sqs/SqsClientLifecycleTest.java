/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.sqs;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

class SqsClientLifecycleTest {

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
}
