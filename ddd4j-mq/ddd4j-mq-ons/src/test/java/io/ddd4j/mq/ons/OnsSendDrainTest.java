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

/** \u901a\u8fc7 <code>verifyRetiredPublisherCannotSendAfterSerialization()</code>\u3001<code>mock()</code>\u3001<code>doAnswer()</code> \u9a8c\u8bc1 <code>OnsSendDrain</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class OnsSendDrainTest {

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>verifyRetiredPublisherCannotSendAfterSerialization()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void rolledBackPublisherChecksRetirementAfterSerialization() throws Exception {
        verifyRetiredPublisherCannotSendAfterSerialization(false);
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>verifyRetiredPublisherCannotSendAfterSerialization()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void closedPublisherChecksBorrowedOwnershipAfterSerialization() throws Exception {
        verifyRetiredPublisherCannotSendAfterSerialization(true);
    }

    /**
     * \u8bfb\u53d6 <code>null</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param borrowed \u4f20\u7ed9 <code>newClient()</code> \u7684 <code>boolean</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>shutdownOvertookSend.get()</code>\u4e3a\u5047\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /**
     * \u6784\u9020 <code>OnsMQClient</code>\u3001<code>OnsProperties</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param producer \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>Producer</code> \u8f93\u5165
     * @param borrowed \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>boolean</code> \u8f93\u5165
     * @param onSerialize \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>Runnable</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>OnsMQClient</code> \u7ed3\u679c
     */
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

    /**
     * \u8bfb\u53d6 <code>event</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>event</code>
     */
    private static MQEvent newEvent() {
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setMsgId("stable-id");
        return event;
    }

    /**
     * \u8bfb\u53d6 <code>thread</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param task \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>FutureTask&lt;Void&gt;</code> \u53c2\u6570 <code>task</code>
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>thread</code>
     */
    private static Thread launch(FutureTask<Void> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * \u8c03\u7528 <code>latch.await()</code>\u3001<code>Thread.currentThread()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param latch \u7528\u4e8e\u540c\u6b65\u6d4b\u8bd5\u7ebf\u7a0b\u7684\u95e8\u95e9
     */
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for the test boundary");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the test boundary", ex);
        }
    }

    /**
     * \u8c03\u7528 <code>System.nanoTime()</code>\u3001<code>TimeUnit.SECONDS.toNanos()</code>\u3001<code>rollback.getState()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param rollback \u4f20\u7ed9 <code>rollback.getState()</code> \u7684 <code>Thread</code> \u8f93\u5165
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
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
