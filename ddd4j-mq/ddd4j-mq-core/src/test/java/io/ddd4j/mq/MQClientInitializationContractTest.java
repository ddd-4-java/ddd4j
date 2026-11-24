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

class MQClientInitializationContractTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

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

    @Test
    void requiredListenerFailureRestoresPreviouslyRegisteredPublisher() throws Exception {
        Map<String, Consumer<MQEvent>> publishers = new ConcurrentHashMap<>();
        Consumer<MQEvent> previousPublisher = event -> { };
        publishers.put("test", previousPublisher);
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publishers);
        TestClient client = new TestClient(false);

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.singletonList(listener(true, "required")),
                        properties(), serialization(), null));

        assertSame(previousPublisher, publishers.get("test"));
        assertSame(publishers, BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    @Test
    void closingClientUnregistersItsPublisher() {
        TestClient client = new TestClient();
        client.init(Collections.emptyList(), properties(), serialization(), null);
        assertNotNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));

        client.close();

        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

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

    @Test
    void producerFailureRestoresPreviousContextValues() {
        MQProperties previousProperties = new MQProperties();
        MQEventSerialization previousSerialization = serialization();
        MQEventStorer<MQEvent> previousStorer = event -> { };
        MQEventStorer<MQEvent> newStorer = event -> { };
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

    @Test
    void producerFailureWithNoStorerPreservesCallbackRegistration() {
        MQEventStorer<MQEvent> previousStorer = event -> { };
        MQEventStorer<MQEvent> replacementStorer = event -> { };
        BaseContext.inject(MQClient.MQ_STORER, previousStorer);
        TestClient client = new TestClient().withProducerFailure()
                .beforeProducerInit(() -> BaseContext.inject(MQClient.MQ_STORER, replacementStorer));

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.emptyList(), properties(), serialization(), null));

        assertSame(replacementStorer, BaseContext.get(MQClient.MQ_STORER));
    }

    @Test
    void producerFailureDoesNotReplaceNewerContextRegistrations() {
        MQProperties replacementProperties = new MQProperties();
        MQEventSerialization replacementSerialization = serialization();
        MQEventStorer<MQEvent> replacementStorer = event -> { };
        MQEventStorer<MQEvent> installedStorer = event -> { };
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

    @Test
    void failedProducerKeepsNewerClientUsingTheSameContextInstances() {
        MQProperties sharedProperties = properties();
        MQEventSerialization sharedSerialization = serialization();
        MQEventStorer<MQEvent> sharedStorer = event -> { };
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

    @Test
    void overlappingFailuresRestoreHealthyContextAndPublisher() throws Exception {
        MQProperties healthyProperties = properties();
        MQEventSerialization healthySerialization = serialization();
        MQEventStorer<MQEvent> healthyStorer = event -> { };
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
                        Collections.singletonList(required), properties(), serialization(), event -> { }));
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
                    Collections.singletonList(required), properties(), serialization(), event -> { }));
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

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    @Test
    void requiredConsumerFailureDoesNotReplaceNewerPublisherMap() throws Exception {
        Map<String, Consumer<MQEvent>> previousMap = new ConcurrentHashMap<>();
        Consumer<MQEvent> previousPublisher = event -> { };
        previousMap.put("test", previousPublisher);
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, previousMap);
        Map<String, Consumer<MQEvent>> replacementMap = new ConcurrentHashMap<>();
        Consumer<MQEvent> replacementPublisher = event -> { };
        replacementMap.put("test", replacementPublisher);
        TestClient client = new TestClient(false).beforeConsumerInit(
                () -> BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, replacementMap));

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.singletonList(listener(true, "required")),
                        properties(), serialization(), null));

        assertSame(replacementMap, BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
        assertSame(replacementPublisher, replacementMap.get("test"));
    }

    @Test
    void requiredConsumerFailureDoesNotResurrectClearedPublisherEntry() throws Exception {
        Map<String, Consumer<MQEvent>> publishers = new ConcurrentHashMap<>();
        publishers.put("test", event -> { });
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publishers);
        TestClient client = new TestClient(false).beforeConsumerInit(() -> publishers.remove("test"));

        assertThrows(MQInitializationException.class,
                () -> client.init(Collections.singletonList(listener(true, "required")),
                        properties(), serialization(), null));

        assertNull(publishers.get("test"));
        assertSame(publishers, BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

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

    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("test");
        return properties;
    }

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

    private MQListener listener(boolean required, String group) throws Exception {
        Method method = Handler.class.getMethod("handle", MQEvent.class);
        return MQListener.builder().bean(new Handler()).method(method).group(group).namespace("")
                .topic("orders").tags("*").supports(Collections.singletonList("*"))
                .separator(".").required(required).build();
    }

    public static final class Handler {
        public void handle(MQEvent event) {
        }
    }

    private static final class TestClient implements MQClient {
        private final boolean[] results;
        private boolean failProducer;
        private Runnable producerInitHook = () -> { };
        private Runnable consumerInitHook = () -> { };
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger closedResources = new AtomicInteger();
        private final AtomicInteger publishedEvents = new AtomicInteger();
        private final MQClientLifecycle lifecycle = new MQClientLifecycle();
        private final MQStartupStatus status = new MQStartupStatus("test");

        private TestClient(boolean... results) {
            this.results = results;
        }

        private TestClient withProducerFailure() {
            failProducer = true;
            return this;
        }

        private TestClient beforeProducerInit(Runnable hook) {
            producerInitHook = hook;
            return this;
        }

        private TestClient beforeConsumerInit(Runnable hook) {
            consumerInitHook = hook;
            return this;
        }

        @Override
        public String impl() {
            return "test";
        }

        @Override
        public MQClientLifecycle lifecycle() {
            return lifecycle;
        }

        @Override
        public MQStartupStatus startupStatus() {
            return status;
        }

        @Override
        public Consumer<MQEvent> initProducer(MQProperties properties) {
            lifecycle.register("producer", closedResources::incrementAndGet);
            producerInitHook.run();
            if (failProducer) {
                throw new IllegalStateException("producer unavailable");
            }
            return event -> publishedEvents.incrementAndGet();
        }

        @Override
        public boolean initConsumer(MQListener listener, MQProperties properties) {
            consumerInitHook.run();
            int index = attempts.getAndIncrement();
            lifecycle.register("consumer-" + index, closedResources::incrementAndGet);
            return results[index];
        }
    }
}
