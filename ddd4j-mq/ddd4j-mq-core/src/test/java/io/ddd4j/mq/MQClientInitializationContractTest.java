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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
        private Runnable consumerInitHook = () -> { };
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger closedResources = new AtomicInteger();
        private final MQClientLifecycle lifecycle = new MQClientLifecycle();
        private final MQStartupStatus status = new MQStartupStatus("test");

        private TestClient(boolean... results) {
            this.results = results;
        }

        private TestClient withProducerFailure() {
            failProducer = true;
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
            if (failProducer) {
                throw new IllegalStateException("producer unavailable");
            }
            return event -> {
            };
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
