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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 通过 <code>verifyRetiredPublisherCannotSendAfterSerialization()</code>、<code>mock()</code>、<code>doAnswer()</code> 验证 <code>OnsSendDrain</code> 的运行行为。
 */
class OnsSendDrainTest {

    /**
     * 读取 <code>null</code> 供当前测试使用。
     *
     * @param borrowed 传给 <code>newClient()</code> 的 <code>boolean</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 构造 <code>OnsMQClient</code>、<code>OnsProperties</code> 作为当前测试的输入或结果。
     *
     * @param producer    用于构造本次返回结果的 <code>Producer</code> 输入
     * @param borrowed    用于构造本次返回结果的 <code>boolean</code> 输入
     * @param onSerialize 用于构造本次返回结果的 <code>Runnable</code> 输入
     * @return 提供给后续契约断言的 <code>OnsMQClient</code> 结果
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
     * 读取 <code>event</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>event</code>
     */
    private static MQEvent newEvent() {
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setMsgId("stable-id");
        return event;
    }

    /**
     * 读取 <code>thread</code> 供当前测试使用。
     *
     * @param task 本次调用使用的 <code>FutureTask&lt;Void&gt;</code> 参数 <code>task</code>
     * @param name 当前测试指定的对象或属性名称
     * @return 当前测试使用的结果，取自 <code>thread</code>
     */
    private static Thread launch(FutureTask<Void> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 调用 <code>latch.await()</code>、<code>Thread.currentThread()</code> 执行当前夹具操作。
     *
     * @param latch 用于同步测试线程的门闩
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
     * 调用 <code>System.nanoTime()</code>、<code>TimeUnit.SECONDS.toNanos()</code>、<code>rollback.getState()</code> 执行当前夹具操作。
     *
     * @param rollback 传给 <code>rollback.getState()</code> 的 <code>Thread</code> 输入
     * @throws InterruptedException 等待测试操作时被中断
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

    /**
     * 在测试框架中执行 <code>verifyRetiredPublisherCannotSendAfterSerialization()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void rolledBackPublisherChecksRetirementAfterSerialization() throws Exception {
        verifyRetiredPublisherCannotSendAfterSerialization(false);
    }

    /**
     * 在测试框架中执行 <code>verifyRetiredPublisherCannotSendAfterSerialization()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void closedPublisherChecksBorrowedOwnershipAfterSerialization() throws Exception {
        verifyRetiredPublisherCannotSendAfterSerialization(true);
    }

    /**
     * 验证<code>shutdownOvertookSend.get()</code>为假。
     *
     * @throws Exception 等待并发操作、发起测试请求时失败
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

        try (OnsMQClient client = newClient(producer, false, () -> {
        })) {
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
}
