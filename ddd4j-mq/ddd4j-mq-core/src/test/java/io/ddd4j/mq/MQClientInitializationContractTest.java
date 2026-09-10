/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            @Override @SuppressWarnings("unchecked") public <T> T serialize(Object event) { return (T) "{}"; }
            @Override public <S, T> T deserialize(S value, Class<T> type) { return null; }
        };
    }

    private MQListener listener(boolean required, String group) throws Exception {
        Method method = Handler.class.getMethod("handle", MQEvent.class);
        return MQListener.builder().bean(new Handler()).method(method).group(group).namespace("")
                .topic("orders").tags("*").supports(Collections.singletonList("*"))
                .separator(".").required(required).build();
    }

    public static final class Handler {
        public void handle(MQEvent event) { }
    }

    private static final class TestClient implements MQClient {
        private final boolean[] results;
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger closedResources = new AtomicInteger();
        private final MQClientLifecycle lifecycle = new MQClientLifecycle();
        private final MQStartupStatus status = new MQStartupStatus("test");

        private TestClient(boolean... results) {
            this.results = results;
        }

        @Override public String impl() { return "test"; }
        @Override public MQClientLifecycle lifecycle() { return lifecycle; }
        @Override public MQStartupStatus startupStatus() { return status; }
        @Override public Consumer<MQEvent> initProducer(MQProperties properties) {
            lifecycle.register("producer", closedResources::incrementAndGet);
            return event -> { };
        }
        @Override public boolean initConsumer(MQListener listener, MQProperties properties) {
            int index = attempts.getAndIncrement();
            lifecycle.register("consumer-" + index, closedResources::incrementAndGet);
            return results[index];
        }
    }
}
