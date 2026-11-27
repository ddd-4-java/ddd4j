/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.sqs;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.lifecycle.MQInitializationException;
import io.ddd4j.mq.lifecycle.MQStartupState;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证 <code>SqsClientLifecycle</code> 在 <code>consumer</code>、<code>requiredListenerRollbackAllowsSameAdapterToRestart</code>、<code>closedAdapter</code>、<code>invalidPollInterval</code> 等场景中的行为。
 */
class SqsClientLifecycleTest {

    /**
     * 验证<code>adapter.initConsumer(listener, properties)</code>为真，<code>polled.await(5, TimeUnit.SECONDS)</code>为真。
     *
     * @throws Exception 等待并发操作时失败
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

    /**
     * 验证<code>adapter.init(Collections.singletonList(listener), properties, serialization, null)</code> 抛出 <code>MQInitializationException.class</code>，<code>adapter.startupStatus().snapshot().state()</code> 等于 <code>MQStartupState.FAILED</code>，<code>polled.await(5, TimeUnit.SECONDS)</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void requiredListenerRollbackAllowsSameAdapterToRestartWithNewOwnedClient() throws Exception {
        SqsClient firstClient = mock(SqsClient.class);
        SqsClient restartedClient = mock(SqsClient.class);
        AtomicInteger creations = new AtomicInteger();
        CountDownLatch polled = new CountDownLatch(1);
        when(restartedClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            polled.countDown();
            return ReceiveMessageResponse.builder().build();
        });
        SqsProperties properties = new SqsProperties() {
            @Override
            public SqsClient client() {
                return creations.getAndIncrement() == 0 ? firstClient : restartedClient;
            }
        };
        properties.setEnabled(true);
        properties.setBroker("sqs");
        properties.setPollIntervalMs(1000L);
        MQListener listener = new MQListener();
        listener.setTopic("invalid-queue");
        SqsMQClient adapter = new SqsMQClient(properties);
        MQEventSerialization serialization = mock(MQEventSerialization.class);
        Object previousProperties = BaseContext.get(MQEvent.MQ_PROPERTIES);
        Object previousSerialization = BaseContext.get(MQClient.MQ_SERIALIZATION);

        try {
            assertThrows(MQInitializationException.class,
                    () -> adapter.init(Collections.singletonList(listener), properties, serialization, null));
            assertEquals(MQStartupState.FAILED, adapter.startupStatus().snapshot().state());
            verify(firstClient, times(1)).close();

            listener.setTopic("https://sqs.example/queue");
            adapter.init(Collections.singletonList(listener), properties, serialization, null);
            assertEquals(2, creations.get(), "Retry must create a fresh owned client");
            assertEquals(MQStartupState.READY, adapter.startupStatus().snapshot().state());
            assertTrue(polled.await(5, TimeUnit.SECONDS));

            adapter.close();
            verify(firstClient, times(1)).close();
            verify(restartedClient, times(1)).close();
        } finally {
            adapter.close();
            BaseContext.replaceIfCurrent(MQEvent.MQ_PROPERTIES, properties, previousProperties);
            BaseContext.replaceIfCurrent(MQClient.MQ_SERIALIZATION, serialization, previousSerialization);
        }
    }

    /**
     * 验证<code>adapter.init(Collections.singletonList(listener), properties, serialization, null)</code> 抛出 <code>MQInitializationException.class</code>，<code>adapter.initConsumer(listener, properties)</code>为真，<code>adapter.lifecycle().checkpoint()</code> 等于 <code>2</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void requiredListenerRollbackLeavesBorrowedClientOpenForRetry() throws Exception {
        SqsClient borrowedClient = mock(SqsClient.class);
        CountDownLatch polled = new CountDownLatch(1);
        when(borrowedClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            polled.countDown();
            return ReceiveMessageResponse.builder().build();
        });
        SqsProperties properties = new SqsProperties();
        properties.setEnabled(true);
        properties.setBroker("sqs");
        MQListener listener = new MQListener();
        listener.setTopic("invalid-queue");
        SqsMQClient adapter = new SqsMQClient(borrowedClient);
        MQEventSerialization serialization = mock(MQEventSerialization.class);

        try {
            assertThrows(MQInitializationException.class,
                    () -> adapter.init(Collections.singletonList(listener), properties, serialization, null));
            verify(borrowedClient, never()).close();

            listener.setTopic("https://sqs.example/queue");
            assertTrue(adapter.initConsumer(listener, properties));
            assertTrue(polled.await(5, TimeUnit.SECONDS));
            assertEquals(2, adapter.lifecycle().checkpoint());
        } finally {
            adapter.close();
        }
        verify(borrowedClient, never()).close();
    }

    /**
     * 验证<code>adapter.initConsumer(listener, properties)</code>为真，<code>callbackStarted.await(5, TimeUnit.SECONDS)</code>为真，<code>visibilityClient.get()</code> 与…为同一实例 <code>originalClient</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void lateFailedCallbackResetsVisibilityOnTheReceivingClientAfterRollback() throws Exception {
        SqsClient originalClient = mock(SqsClient.class);
        SqsClient replacementClient = mock(SqsClient.class);
        AtomicInteger creations = new AtomicInteger();
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        CountDownLatch visibilityReset = new CountDownLatch(1);
        AtomicReference<SqsClient> visibilityClient = new AtomicReference<>();
        Message delivery = Message.builder().body("{}").receiptHandle("old-receipt").build();
        when(originalClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(delivery).build());
        when(replacementClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().build());
        for (SqsClient nativeClient : new SqsClient[]{originalClient, replacementClient}) {
            when(nativeClient.changeMessageVisibility(
                    org.mockito.ArgumentMatchers.<Consumer<ChangeMessageVisibilityRequest.Builder>>any()))
                    .thenAnswer(invocation -> {
                        Consumer<ChangeMessageVisibilityRequest.Builder> configure = invocation.getArgument(0);
                        ChangeMessageVisibilityRequest.Builder request = ChangeMessageVisibilityRequest.builder();
                        configure.accept(request);
                        if ("old-receipt".equals(request.build().receiptHandle())) {
                            visibilityClient.set(nativeClient);
                            visibilityReset.countDown();
                        }
                        return ChangeMessageVisibilityResponse.builder().build();
                    });
        }
        SqsProperties properties = new SqsProperties() {
            @Override
            public SqsClient client() {
                return creations.getAndIncrement() == 0 ? originalClient : replacementClient;
            }
        };
        properties.setPollIntervalMs(1000L);
        MQListener listener = new MQListener();
        listener.setTopic("https://sqs.example/queue");
        MQEventSerialization serialization = mock(MQEventSerialization.class);
        when(serialization.deserialize(anyString(), eq(MQEvent.class))).thenReturn(new MQEvent());
        SqsMQClient adapter = new SqsMQClient(properties) {
            @Override
            public MQEventSerialization serialization() {
                return serialization;
            }

            @Override
            public void consume(MQListener binding, MQEvent event, Acknowledgment acknowledgment) throws Throwable {
                callbackStarted.countDown();
                while (releaseCallback.getCount() > 0) {
                    try {
                        if (!releaseCallback.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("The test did not release the blocked SQS callback");
                        }
                    } catch (InterruptedException ignored) {
                        // The listener remains in flight even when its poller is interrupted.
                    }
                }
                throw new IllegalStateException("listener failed");
            }
        };

        try {
            int checkpoint = adapter.lifecycle().checkpoint();
            assertTrue(adapter.initConsumer(listener, properties));
            assertTrue(callbackStarted.await(5, TimeUnit.SECONDS));
            adapter.lifecycle().rollback(checkpoint);
            assertTrue(adapter.initConsumer(listener, properties));
            assertEquals(2, creations.get());
            releaseCallback.countDown();

            assertTrue(visibilityReset.await(5, TimeUnit.SECONDS));
            assertSame(originalClient, visibilityClient.get(), "Old receipt must use the original SQS client");
        } finally {
            releaseCallback.countDown();
            adapter.close();
        }
    }

    /**
     * 验证<code>releaseSend.await(5, TimeUnit.SECONDS)</code>为真，<code>failListener.await(5, TimeUnit.SECONDS)</code>为真，<code>failure.getCause() instanceof MQInitializationException</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void requiredListenerRollbackWaitsForAnInFlightSendBeforeClosingOwnedClient() throws Exception {
        SqsClient nativeClient = mock(SqsClient.class);
        CountDownLatch initializingListener = new CountDownLatch(1);
        CountDownLatch failListener = new CountDownLatch(1);
        CountDownLatch listenerFailing = new CountDownLatch(1);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        CountDownLatch nativeClosed = new CountDownLatch(1);
        when(nativeClient.sendMessage(any(SendMessageRequest.class))).thenAnswer(invocation -> {
            sending.countDown();
            assertTrue(releaseSend.await(5, TimeUnit.SECONDS));
            return SendMessageResponse.builder().build();
        });
        doAnswer(invocation -> {
            nativeClosed.countDown();
            return null;
        }).when(nativeClient).close();
        SqsProperties properties = new SqsProperties() {
            @Override
            public SqsClient client() {
                return nativeClient;
            }
        };
        properties.setEnabled(true);
        properties.setBroker("sqs");
        MQListener listener = new MQListener();
        listener.setTopic("invalid-queue");
        SqsMQClient adapter = new SqsMQClient(properties) {
            @Override
            public synchronized boolean initConsumer(MQListener binding, MQProperties configuration)
                    throws Exception {
                initializingListener.countDown();
                assertTrue(failListener.await(5, TimeUnit.SECONDS));
                listenerFailing.countDown();
                return super.initConsumer(binding, configuration);
            }
        };
        MQEventSerialization serialization = mock(MQEventSerialization.class);
        when(serialization.serialize(any(MQEvent.class))).thenReturn("{}");
        MQEvent event = new MQEvent();
        event.setTopic("https://sqs.example/queue");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> initializing = executor.submit(() ->
                    adapter.init(Collections.singletonList(listener), properties, serialization, null));
            assertTrue(initializingListener.await(5, TimeUnit.SECONDS));
            Map<String, Consumer<MQEvent>> publishers = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
            Future<?> publishing = executor.submit(() -> publishers.get("sqs").accept(event));
            assertTrue(sending.await(5, TimeUnit.SECONDS));

            failListener.countDown();
            assertTrue(listenerFailing.await(5, TimeUnit.SECONDS));
            assertFalse(nativeClosed.await(150, TimeUnit.MILLISECONDS),
                    "Startup rollback must not close the owned SDK client during a native send");

            releaseSend.countDown();
            publishing.get(5, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> initializing.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof MQInitializationException);
            verify(nativeClient, times(1)).close();
        } finally {
            failListener.countDown();
            releaseSend.countDown();
            executor.shutdownNow();
            adapter.close();
        }
    }

    /**
     * 验证<code>adapter.initConsumer(listener, properties)</code> 抛出 <code>IllegalStateException.class</code>，<code>creations.get()</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>adapter.initConsumer(listener, properties)</code> 抛出 <code>IllegalArgumentException.class</code>，<code>creations.get()</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>publisher.accept(new MQEvent())</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage()</code> 等于 <code>"SQS client is closed"</code>。
     */
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
     * 验证<code>releaseSend.await(5, TimeUnit.SECONDS)</code>为真，<code>closing.await(5, TimeUnit.SECONDS)</code>为真，<code>close.get(150, TimeUnit.MILLISECONDS)</code> 抛出 <code>TimeoutException.class</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>adapter.initConsumer(listener, properties)</code>为真，<code>receiving.await(5, TimeUnit.SECONDS)</code>为真，<code>close.get(150, TimeUnit.MILLISECONDS)</code> 抛出 <code>TimeoutException.class</code>。
     *
     * @throws Exception 等待并发操作时失败
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
