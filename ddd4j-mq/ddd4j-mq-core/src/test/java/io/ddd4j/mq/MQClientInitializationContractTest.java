/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQInitializationException;
import io.ddd4j.mq.lifecycle.MQStartupState;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>MQClientInitializationContract</code> 在 <code>producerFailure</code> 等场景中的行为。
 */
class MQClientInitializationContractTest {

    /**
     * 调用 <code>latch.await()</code>、<code>Thread.currentThread()</code> 执行当前夹具操作。
     *
     * @param latch 用于同步测试线程的门闩
     */
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    /**
     * 测试后依次清除<code>BaseContext</code>。
     */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 验证<code>built.isRequired()</code>为真，<code>legacy.isRequired()</code>为真，<code>MQListener.builder().required(false).build().isRequired()</code>为假。
     */
    @Test
    void listenerIsRequiredByDefaultAndLegacyConstructorRemainsAvailable() {
        MQListener built = MQListener.builder().topic("orders").build();
        MQListener legacy = new MQListener(null, null, "group", "namespace", "orders", "*",
                Collections.singletonList("*"), ".");

        assertTrue(built.isRequired());
        assertTrue(legacy.isRequired());
        assertTrue(built.supports().isEmpty());
        assertFalse(MQListener.builder().required(false).build().isRequired());
    }

    /**
     * 验证<code>client.init(Collections.singletonList(listener(true, "required")), properties(), serialization(), null)</code> 抛出 <code>MQInitializationException.class</code>，<code>exception.topic()</code> 等于 <code>"orders"</code>，<code>BaseContext.get(MQClient.MQ_SERIALIZATION)</code>为空。
     *
     * @throws Exception 读取测试数据或等待测试结果失败
     */
    @Test
    void requiredListenerFailureRollsBackPublisherAndResources() throws Exception {
        TestClient client = new TestClient(false);
        MQInitializationException exception = assertThrows(MQInitializationException.class,
                () -> client.init(Collections.singletonList(listener(true, "required")),
                        properties(), serialization(), null));

        assertEquals("orders", exception.topic());
        assertEquals(2, client.closedResources.get());
        assertEquals(MQStartupState.FAILED, client.startupStatus().snapshot().state());
        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
        assertNull(BaseContext.get(MQEvent.MQ_PROPERTIES));
        assertNull(BaseContext.get(MQClient.MQ_SERIALIZATION));
    }

    /**
     * 验证<code>client.init(Collections.singletonList(listener(true, "required")), properties(), serialization(), null)</code> 抛出 <code>MQInitializationException.class</code>，<code>publishers.get("test")</code> 与…为同一实例 <code>previousPublisher</code>，<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code> 与…为同一实例 <code>publishers</code>。
     *
     * @throws Exception 读取测试数据或等待测试结果失败
     */
    @Test
    void requiredListenerFailureRestoresPreviouslyRegisteredPublisher() throws Exception {
        Map<String, Consumer<MQEvent>> publishers = new ConcurrentHashMap<>();
        Consumer<MQEvent> previousPublisher = event -> {
        };
        publishers.put("test", previousPublisher);
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publishers);
        TestClient client = new TestClient(false);

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.singletonList(listener(true, "required")),
                        properties(), serialization(), null));

        assertSame(previousPublisher, publishers.get("test"));
        assertSame(publishers, BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    /**
     * 验证<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>非空，<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>为空。
     */
    @Test
    void closingClientUnregistersItsPublisher() {
        TestClient client = new TestClient();
        client.init(Collections.emptyList(), properties(), serialization(), null);
        assertNotNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));

        client.close();

        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    /**
     * 验证<code>publishers.get("test")</code> 与…为同一实例 <code>activePublisher</code>，<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>为空。
     */
    @Test
    void closingEarlierClientDoesNotRemoveLaterPublisher() {
        TestClient earlier = new TestClient();
        TestClient later = new TestClient();
        earlier.init(Collections.emptyList(), properties(), serialization(), null);
        later.init(Collections.emptyList(), properties(), serialization(), null);
        Map<String, Consumer<MQEvent>> publishers = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
        Consumer<MQEvent> activePublisher = publishers.get("test");

        earlier.close();
        assertSame(activePublisher, publishers.get("test"));

        later.close();
        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    /**
     * 验证<code>failing.init(Collections.singletonList(listener(true, "required")), properties(), serialization(), null)</code> 抛出 <code>MQInitializationException.class</code>，<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>为空。
     *
     * @throws Exception 读取测试数据或等待测试结果失败
     */
    @Test
    void failedReplacementDoesNotRestorePublisherAlreadyClosedDuringInitialization() throws Exception {
        TestClient earlier = new TestClient();
        earlier.init(Collections.emptyList(), properties(), serialization(), null);
        TestClient failing = new TestClient(false).beforeConsumerInit(earlier::close);

        assertThrows(MQInitializationException.class,
                () -> failing.init(Collections.singletonList(listener(true, "required")),
                        properties(), serialization(), null));

        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    /**
     * 验证<code>client.init(Collections.emptyList(), properties(), serialization(), newStorer)</code> 抛出 <code>MQInitializationException.class</code>，<code>client.closedResources.get()</code> 等于 <code>1</code>，<code>BaseContext.get(MQClient.MQ_STORER)</code> 与…为同一实例 <code>previousStorer</code>。
     */
    @Test
    void producerFailureRestoresPreviousContextValues() {
        MQProperties previousProperties = new MQProperties();
        MQEventSerialization previousSerialization = serialization();
        MQEventStorer<MQEvent> previousStorer = event -> {
        };
        MQEventStorer<MQEvent> newStorer = event -> {
        };
        BaseContext.inject(MQEvent.MQ_PROPERTIES, previousProperties);
        BaseContext.inject(MQClient.MQ_SERIALIZATION, previousSerialization);
        BaseContext.inject(MQClient.MQ_STORER, previousStorer);
        TestClient client = new TestClient().withProducerFailure();

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.emptyList(), properties(), serialization(), newStorer));

        assertEquals(1, client.closedResources.get());
        assertEquals(MQStartupState.FAILED, client.startupStatus().snapshot().state());
        assertSame(previousProperties, BaseContext.get(MQEvent.MQ_PROPERTIES));
        assertSame(previousSerialization, BaseContext.get(MQClient.MQ_SERIALIZATION));
        assertSame(previousStorer, BaseContext.get(MQClient.MQ_STORER));
    }

    /**
     * 验证<code>client.init(Collections.emptyList(), properties(), serialization(), null)</code> 抛出 <code>MQInitializationException.class</code>，<code>BaseContext.get(MQClient.MQ_STORER)</code> 与…为同一实例 <code>replacementStorer</code>。
     */
    @Test
    void producerFailureWithNoStorerPreservesCallbackRegistration() {
        MQEventStorer<MQEvent> previousStorer = event -> {
        };
        MQEventStorer<MQEvent> replacementStorer = event -> {
        };
        BaseContext.inject(MQClient.MQ_STORER, previousStorer);
        TestClient client = new TestClient().withProducerFailure()
                .beforeProducerInit(() -> BaseContext.inject(MQClient.MQ_STORER, replacementStorer));

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.emptyList(), properties(), serialization(), null));

        assertSame(replacementStorer, BaseContext.get(MQClient.MQ_STORER));
    }

    /**
     * 验证<code>client.init(Collections.emptyList(), properties(), serialization(), installedStorer)</code> 抛出 <code>MQInitializationException.class</code>，<code>BaseContext.get(MQEvent.MQ_PROPERTIES)</code> 与…为同一实例 <code>replacementProperties</code>，<code>client.startupStatus().snapshot().state()</code> 等于 <code>MQStartupState.FAILED</code>。
     */
    @Test
    void producerFailureDoesNotReplaceNewerContextRegistrations() {
        MQProperties replacementProperties = new MQProperties();
        MQEventSerialization replacementSerialization = serialization();
        MQEventStorer<MQEvent> replacementStorer = event -> {
        };
        MQEventStorer<MQEvent> installedStorer = event -> {
        };
        TestClient client = new TestClient().withProducerFailure().beforeProducerInit(() -> {
            BaseContext.inject(MQEvent.MQ_PROPERTIES, replacementProperties);
            BaseContext.inject(MQClient.MQ_SERIALIZATION, replacementSerialization);
            BaseContext.inject(MQClient.MQ_STORER, replacementStorer);
        });

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.emptyList(), properties(), serialization(), installedStorer));

        assertAll(
                () -> assertSame(replacementProperties, BaseContext.get(MQEvent.MQ_PROPERTIES)),
                () -> assertSame(replacementSerialization, BaseContext.get(MQClient.MQ_SERIALIZATION)),
                () -> assertSame(replacementStorer, BaseContext.get(MQClient.MQ_STORER)));
        assertEquals(MQStartupState.FAILED, client.startupStatus().snapshot().state());
    }

    /**
     * 验证<code>failing.init(Collections.emptyList(), sharedProperties, sharedSerialization, sharedStorer)</code> 抛出 <code>MQInitializationException.class</code>，<code>failing.startupStatus().snapshot().state()</code> 等于 <code>MQStartupState.FAILED</code>，<code>newer.publishedEvents.get()</code> 等于 <code>1</code>。
     */
    @Test
    void failedProducerKeepsNewerClientUsingTheSameContextInstances() {
        MQProperties sharedProperties = properties();
        MQEventSerialization sharedSerialization = serialization();
        MQEventStorer<MQEvent> sharedStorer = event -> {
        };
        TestClient newer = new TestClient();
        TestClient failing = new TestClient().withProducerFailure().beforeProducerInit(
                () -> newer.init(Collections.emptyList(), sharedProperties, sharedSerialization, sharedStorer));

        assertThrows(MQInitializationException.class, () -> failing.init(Collections.emptyList(),
                sharedProperties, sharedSerialization, sharedStorer));

        assertEquals(MQStartupState.FAILED, failing.startupStatus().snapshot().state());
        assertEquals(MQStartupState.READY, newer.startupStatus().snapshot().state());
        assertAll(
                () -> assertSame(sharedProperties, BaseContext.get(MQEvent.MQ_PROPERTIES)),
                () -> assertSame(sharedSerialization, BaseContext.get(MQClient.MQ_SERIALIZATION)),
                () -> assertSame(sharedStorer, BaseContext.get(MQClient.MQ_STORER)));
        new MQEvent().publish();
        assertEquals(1, newer.publishedEvents.get());
        newer.close();
    }

    /**
     * 验证<code>client.init(Collections.emptyList(), properties(), installed, null)</code> 抛出 <code>MQInitializationException.class</code>，<code>BaseContext.get(MQClient.MQ_SERIALIZATION)</code> 与…为同一实例 <code>installed</code>。
     */
    @Test
    void failedProducerKeepsPlainReinjectionOfItsSerializer() {
        MQEventSerialization installed = serialization();
        BaseContext.inject(MQClient.MQ_SERIALIZATION, serialization());
        TestClient client = new TestClient().withProducerFailure()
                .beforeProducerInit(() -> BaseContext.inject(MQClient.MQ_SERIALIZATION, installed));

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.emptyList(), properties(), installed, null));

        assertSame(installed, BaseContext.get(MQClient.MQ_SERIALIZATION));
    }

    /**
     * 验证被测调用 抛出 <code>MQInitializationException.class</code>，<code>older.startupStatus().snapshot().state()</code> 等于 <code>MQStartupState.FAILED</code>，<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>为空。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void overlappingFailuresRestoreHealthyContextAndPublisher() throws Exception {
        MQProperties healthyProperties = properties();
        MQEventSerialization healthySerialization = serialization();
        MQEventStorer<MQEvent> healthyStorer = event -> {
        };
        TestClient healthy = new TestClient();
        healthy.init(Collections.emptyList(), healthyProperties, healthySerialization, healthyStorer);
        CountDownLatch olderInConsumer = new CountDownLatch(1);
        CountDownLatch releaseOlder = new CountDownLatch(1);
        CountDownLatch olderFailed = new CountDownLatch(1);
        TestClient older = new TestClient(false).beforeConsumerInit(() -> {
            olderInConsumer.countDown();
            await(releaseOlder);
        });
        MQListener required = listener(true, "required");
        FutureTask<MQInitializationException> olderFailure = new FutureTask<>(() -> {
            try {
                return assertThrows(MQInitializationException.class, () -> older.init(
                        Collections.singletonList(required), properties(), serialization(), event -> {
                        }));
            } finally {
                olderFailed.countDown();
            }
        });
        Thread olderThread = new Thread(olderFailure, "mq-older-failing-init");
        olderThread.setDaemon(true);
        olderThread.start();

        TestClient newer = new TestClient(false).beforeConsumerInit(() -> {
            releaseOlder.countDown();
            await(olderFailed);
        });
        try {
            await(olderInConsumer);
            assertThrows(MQInitializationException.class, () -> newer.init(
                    Collections.singletonList(required), properties(), serialization(), event -> {
                    }));
        } finally {
            releaseOlder.countDown();
        }
        olderFailure.get(5, TimeUnit.SECONDS);

        assertEquals(MQStartupState.FAILED, older.startupStatus().snapshot().state());
        assertEquals(MQStartupState.FAILED, newer.startupStatus().snapshot().state());
        assertEquals(MQStartupState.READY, healthy.startupStatus().snapshot().state());
        new MQEvent().publish();
        assertAll(
                () -> assertSame(healthyProperties, BaseContext.get(MQEvent.MQ_PROPERTIES)),
                () -> assertSame(healthySerialization, BaseContext.get(MQClient.MQ_SERIALIZATION)),
                () -> assertSame(healthyStorer, BaseContext.get(MQClient.MQ_STORER)),
                () -> assertEquals(1, healthy.publishedEvents.get()));
        healthy.close();
        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    /**
     * 验证<code>client.init(Collections.singletonList(listener(true, "required")), properties(), serialization(), null)</code> 抛出 <code>MQInitializationException.class</code>，<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code> 与…为同一实例 <code>replacementMap</code>，<code>replacementMap.get("test")</code> 与…为同一实例 <code>replacementPublisher</code>。
     *
     * @throws Exception 读取测试数据或等待测试结果失败
     */
    @Test
    void requiredConsumerFailureDoesNotReplaceNewerPublisherMap() throws Exception {
        Map<String, Consumer<MQEvent>> previousMap = new ConcurrentHashMap<>();
        Consumer<MQEvent> previousPublisher = event -> {
        };
        previousMap.put("test", previousPublisher);
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, previousMap);
        Map<String, Consumer<MQEvent>> replacementMap = new ConcurrentHashMap<>();
        Consumer<MQEvent> replacementPublisher = event -> {
        };
        replacementMap.put("test", replacementPublisher);
        TestClient client = new TestClient(false).beforeConsumerInit(
                () -> BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, replacementMap));

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.singletonList(listener(true, "required")),
                        properties(), serialization(), null));

        assertSame(replacementMap, BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
        assertSame(replacementPublisher, replacementMap.get("test"));
    }

    /**
     * 验证<code>client.init(Collections.singletonList(listener(true, "required")), properties(), serialization(), null)</code> 抛出 <code>MQInitializationException.class</code>，<code>publishers.get("test")</code>为空，<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code> 与…为同一实例 <code>publishers</code>。
     *
     * @throws Exception 读取测试数据或等待测试结果失败
     */
    @Test
    void requiredConsumerFailureDoesNotResurrectClearedPublisherEntry() throws Exception {
        Map<String, Consumer<MQEvent>> publishers = new ConcurrentHashMap<>();
        publishers.put("test", event -> {
        });
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publishers);
        TestClient client = new TestClient(false).beforeConsumerInit(() -> publishers.remove("test"));

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.singletonList(listener(true, "required")),
                        properties(), serialization(), null));

        assertNull(publishers.get("test"));
        assertSame(publishers, BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    /**
     * 验证<code>releaseReplacement.await(10, TimeUnit.SECONDS)</code>为真，<code>replacementEntered.await(5, TimeUnit.SECONDS)</code>为真，<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>为空。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void closingEarlierClientWhileReplacingPublisherKeepsNewRegistration() throws Exception {
        CountDownLatch replacementEntered = new CountDownLatch(1);
        CountDownLatch releaseReplacement = new CountDownLatch(1);
        CountDownLatch closeFinished = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        Map<String, Consumer<MQEvent>> publishers = new ConcurrentHashMap<String, Consumer<MQEvent>>() {
            @Override
            public Consumer<MQEvent> put(String key, Consumer<MQEvent> value) {
                if (writes.incrementAndGet() == 2) {
                    replacementEntered.countDown();
                    try {
                        assertTrue(releaseReplacement.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return super.put(key, value);
            }
        };
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publishers);
        TestClient earlier = new TestClient();
        earlier.init(Collections.emptyList(), properties(), serialization(), null);
        TestClient later = new TestClient();
        FutureTask<Void> replacement = new FutureTask<>(() -> {
            later.init(Collections.emptyList(), properties(), serialization(), null);
            return null;
        });
        FutureTask<Void> close = new FutureTask<>(() -> {
            try {
                earlier.close();
                return null;
            } finally {
                closeFinished.countDown();
            }
        });
        Thread replacingThread = new Thread(replacement, "mq-replacement");
        Thread closingThread = new Thread(close, "mq-close");
        replacingThread.setDaemon(true);
        closingThread.setDaemon(true);
        replacingThread.start();
        try {
            assertTrue(replacementEntered.await(5, TimeUnit.SECONDS));
            closingThread.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!closeFinished.await(1, TimeUnit.MILLISECONDS)) {
                ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(closingThread.getId());
                if (Objects.nonNull(info) && info.getThreadState() == Thread.State.BLOCKED
                        && info.getLockOwnerId() == replacingThread.getId()) {
                    break;
                }
                assertTrue(System.nanoTime() < deadline, "close must finish or wait for the registry monitor");
            }
        } finally {
            releaseReplacement.countDown();
        }
        replacement.get(5, TimeUnit.SECONDS);
        close.get(5, TimeUnit.SECONDS);

        assertSame(publishers, BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
        assertNotNull(publishers.get("test"));
        later.close();
        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    /**
     * 验证<code>client.attempts.get()</code> 等于 <code>2</code>，<code>client.closedResources.get()</code> 等于 <code>1</code>，<code>client.startupStatus().snapshot().failures().get(0).required()</code>为假。
     *
     * @throws Exception 读取测试数据或等待测试结果失败
     */
    @Test
    void optionalFailureContinuesAndMarksClientDegraded() throws Exception {
        TestClient client = new TestClient(false, true);

        client.init(Arrays.asList(listener(false, "optional"), listener(true, "required")),
                properties(), serialization(), null);

        assertEquals(2, client.attempts.get());
        assertEquals(1, client.closedResources.get());
        assertEquals(MQStartupState.DEGRADED, client.startupStatus().snapshot().state());
        assertFalse(client.startupStatus().snapshot().failures().get(0).required());
    }

    /**
     * 读取 <code>properties</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>properties</code>
     */
    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("test");
        return properties;
    }

    /**
     * 构造 <code>MQEventSerialization</code> 作为当前测试的输入或结果。
     *
     * @return 提供给后续契约断言的 <code>MQEventSerialization</code> 结果
     */
    private MQEventSerialization serialization() {
        return new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return null;
            }
        };
    }

    /**
     * 调用 <code>Handler.class.getMethod()</code>、<code>MQListener.builder()</code>、<code>Collections.singletonList()</code> 执行当前夹具操作。
     *
     * @param required 传给 <code>MQListener.builder()</code> 的 <code>boolean</code> 输入
     * @param group    传给 <code>MQListener.builder()</code> 的 <code>String</code> 输入
     * @return 提供给后续契约断言的 <code>MQListener</code> 结果
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private MQListener listener(boolean required, String group) throws Exception {
        Method method = Handler.class.getMethod("handle", MQEvent.class);
        return MQListener.builder().bean(new Handler()).method(method).group(group).namespace("")
                .topic("orders").tags("*").supports(Collections.singletonList("*"))
                .separator(".").required(required).build();
    }

    /**
     * 提供 <code>handle()</code> 等供当前测试使用的独立操作。
     */
    public static final class Handler {
        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        public void handle(MQEvent event) {
        }
    }

    /**
     * 实现 <code>MQClient</code>，供测试调用 <code>withProducerFailure()</code>、<code>beforeProducerInit()</code>、<code>beforeConsumerInit()</code> 并观察结果。
     */
    private static final class TestClient implements MQClient {
        /**
         * 保存请求、回调或被测方法返回的结果。
         */
        private final boolean[] results;
        /**
         * 跟踪测试操作的调用或重试次数。
         */
        private final AtomicInteger attempts = new AtomicInteger();
        /**
         * 在测试线程之间共享当前操作次数或更新数量的计数器。
         */
        private final AtomicInteger closedResources = new AtomicInteger();
        /**
         * 事件发布器实际收到的事件，供后续断言使用。
         */
        private final AtomicInteger publishedEvents = new AtomicInteger();
        /**
         * 协调消息客户端启动、初始化和关闭的生命周期。
         */
        private final MQClientLifecycle lifecycle = new MQClientLifecycle();
        /**
         * 保存消息组件当前启动结果的状态对象。
         */
        private final MQStartupStatus status = new MQStartupStatus("test");
        /**
         * 控制当前测试代理生产者是否装配失败。
         */
        private boolean failProducer;
        /**
         * 用于控制原生生产者初始化结果的测试回调。
         */
        private Runnable producerInitHook = () -> {
        };
        /**
         * 测试控制消费者初始化结果的回调。
         */
        private Runnable consumerInitHook = () -> {
        };

        /**
         * 设置 <code>results</code>，用于构造 <code>TestClient</code> 测试实例。
         *
         * @param results 用于初始化 <code>TestClient</code> 的 <code>boolean[]</code> 值
         */
        private TestClient(boolean... results) {
            this.results = results;
        }

        /**
         * 读取 <code>this</code> 供当前测试使用。
         *
         * @return 当前测试使用的结果，取自 <code>this</code>
         */
        private TestClient withProducerFailure() {
            failProducer = true;
            return this;
        }

        /**
         * 读取 <code>this</code> 供当前测试使用。
         *
         * @param hook 本次调用使用的 <code>Runnable</code> 参数 <code>hook</code>
         * @return 当前测试使用的结果，取自 <code>this</code>
         */
        private TestClient beforeProducerInit(Runnable hook) {
            producerInitHook = hook;
            return this;
        }

        /**
         * 读取 <code>this</code> 供当前测试使用。
         *
         * @param hook 本次调用使用的 <code>Runnable</code> 参数 <code>hook</code>
         * @return 当前测试使用的结果，取自 <code>this</code>
         */
        private TestClient beforeConsumerInit(Runnable hook) {
            consumerInitHook = hook;
            return this;
        }

        /**
         * 返回此消息适配器在 DDD4J 中登记的实现名。
         *
         * @return 实际返回<code>"test"</code>，供调用方取得当前值
         */
        @Override
        public String impl() {
            return "test";
        }

        /**
         * 返回管理连接、生产者和消费者资源的生命周期对象。
         *
         * @return 实际返回<code>lifecycle</code>，供调用方取得当前值
         */
        @Override
        public MQClientLifecycle lifecycle() {
            return lifecycle;
        }

        /**
         * 返回消息客户端当前记录的启动状态。
         *
         * @return 实际返回<code>status</code>，供调用方取得当前值
         */
        @Override
        public MQStartupStatus startupStatus() {
            return status;
        }

        /**
         * 准备消息生产者并返回可发布事件的入口。
         *
         * <p>测试夹具会调用 <code>producerInitHook.run()</code>。</p>
         *
         * @param properties MQ 配置
         * @return 发布函数，null 表示不提供发布能力
         */
        @Override
        public Consumer<MQEvent> initProducer(MQProperties properties) {
            lifecycle.register("producer", closedResources::incrementAndGet);
            producerInitHook.run();
            if (failProducer) {
                throw new IllegalStateException("producer unavailable");
            }
            return event -> publishedEvents.incrementAndGet();
        }

        /**
         * 为监听器建立消息消费所需的订阅和客户端资源。
         *
         * <p>测试夹具会调用 <code>consumerInitHook.run()</code>、<code>attempts.getAndIncrement()</code>。</p>
         *
         * @param listener   监听器定义
         * @param properties MQ 配置
         * @return 实际返回<code>results[index]</code>
         */
        @Override
        public boolean initConsumer(MQListener listener, MQProperties properties) {
            consumerInitHook.run();
            int index = attempts.getAndIncrement();
            lifecycle.register("consumer-" + index, closedResources::incrementAndGet);
            return results[index];
        }
    }
}
