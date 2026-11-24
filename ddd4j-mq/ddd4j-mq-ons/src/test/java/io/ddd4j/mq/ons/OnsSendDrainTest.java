/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OnsSendDrainTest {

    @Test
    void rolledBackPublisherChecksRetirementAfterSerialization() throws Exception {
        verifyRetiredPublisherCannotSendAfterSerialization(false);
    }

    @Test
    void closedPublisherChecksBorrowedOwnershipAfterSerialization() throws Exception {
        verifyRetiredPublisherCannotSendAfterSerialization(true);
    }

    private static void verifyRetiredPublisherCannotSendAfterSerialization(boolean borrowed) throws Exception {
        Producer producer = mock(Producer.class);
        CountDownLatch serializationEntered = new CountDownLatch(1);
        CountDownLatch releaseSerialization = new CountDownLatch(1);
        AtomicBoolean firstSerialization = new AtomicBoolean(true);
        try (OnsMQClient client = newClient(producer, borrowed, () -> {
            if (firstSerialization.compareAndSet(true, false)) {
                serializationEntered.countDown();
                await(releaseSerialization);
            }
        })) {
            int checkpoint = client.lifecycle().checkpoint();
            Consumer<MQEvent> publisher = client.initProducer(new MQProperties());
            FutureTask<Void> send = new FutureTask<>(() -> {
                publisher.accept(newEvent());
                return null;
            });
            launch(send, "ons-serialization");
            try {
                await(serializationEntered);
                FutureTask<Void> retire = new FutureTask<>(() -> {
                    if (borrowed) {
                        client.close();
                    } else {
                        client.lifecycle().rollback(checkpoint);
                    }
                    return null;
                });
                launch(retire, "ons-retire");
                retire.get(5, TimeUnit.SECONDS);
            } finally {
                releaseSerialization.countDown();
            }
            ExecutionException failure = assertThrows(ExecutionException.class, () -> send.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertEquals(borrowed ? "ONS client is closed" : "ONS producer is no longer active",
                    failure.getCause().getMessage());
            verify(producer, never()).send(any(Message.class));
            verify(producer, times(borrowed ? 0 : 1)).shutdown();
        }
    }

    @Test
    void rollbackDrainsAnAdmittedNativeSendBeforeShuttingDown() throws Exception {
        Producer producer = mock(Producer.class);
        CountDownLatch nativeSendEntered = new CountDownLatch(1);
        CountDownLatch releaseNativeSend = new CountDownLatch(1);
        AtomicBoolean nativeSendReturned = new AtomicBoolean();
        AtomicBoolean shutdownOvertookSend = new AtomicBoolean();
        doAnswer(invocation -> {
            nativeSendEntered.countDown();
            await(releaseNativeSend);
            nativeSendReturned.set(true);
            return null;
        }).when(producer).send(any(Message.class));
        doAnswer(invocation -> {
            shutdownOvertookSend.set(!nativeSendReturned.get());
            return null;
        }).when(producer).shutdown();

        try (OnsMQClient client = newClient(producer, false, () -> { })) {
            int checkpoint = client.lifecycle().checkpoint();
            Consumer<MQEvent> publisher = client.initProducer(new MQProperties());
            CountDownLatch rollbackEntered = new CountDownLatch(1);
            client.lifecycle().register("ons-rollback-test-marker", rollbackEntered::countDown);
            FutureTask<Void> send = new FutureTask<>(() -> {
                publisher.accept(newEvent());
                return null;
            });
            launch(send, "ons-native-send");
            FutureTask<Void> rollback = new FutureTask<>(() -> {
                client.lifecycle().rollback(checkpoint);
                return null;
            });
            try {
                await(nativeSendEntered);
                Thread rollbackThread = launch(rollback, "ons-rollback");
                await(rollbackEntered);
                awaitWaitingOrFinished(rollbackThread);
                assertFalse(shutdownOvertookSend.get(), "SDK shutdown overtook an admitted native send");
            } finally {
                releaseNativeSend.countDown();
            }
            send.get(5, TimeUnit.SECONDS);
            rollback.get(5, TimeUnit.SECONDS);
            assertFalse(shutdownOvertookSend.get(), "SDK shutdown overtook an admitted native send");
            verify(producer, times(1)).shutdown();
        }
    }

    private static OnsMQClient newClient(Producer producer, boolean borrowed, Runnable onSerialize) {
        return new OnsMQClient(borrowed ? producer : null, new OnsProperties() {
            @Override
            public Properties sessionProperties(String groupName) {
                return new Properties();
            }
        }) {
            @Override
            Producer createProducer(Properties sessionProperties) {
                return producer;
            }

            @Override
            public MQEventSerialization serialization() {
                return new MQEventSerialization() {
                    @Override
                    public <S, T> T deserialize(S source, Class<T> type) {
                        return type.cast(new MQEvent());
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T serialize(Object event) {
                        onSerialize.run();
                        return (T) "{}";
                    }
                };
            }
        };
    }

    private static MQEvent newEvent() {
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setMsgId("stable-id");
        return event;
    }

    private static Thread launch(FutureTask<Void> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for the test boundary");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the test boundary", ex);
        }
    }

    private static void awaitWaitingOrFinished(Thread rollback) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = rollback.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TERMINATED) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(1);
        }
        throw new AssertionError("Rollback did not reach the release boundary");
    }
}
