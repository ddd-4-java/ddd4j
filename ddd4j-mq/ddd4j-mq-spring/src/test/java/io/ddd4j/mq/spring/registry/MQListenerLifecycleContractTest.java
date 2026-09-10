/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MQListenerLifecycleContractTest {

    @Test
    void requiredClientInitializationFailureEscapesContextRefresh() {
        MQClient failing = new StubClient("failing", null) {
            @Override
            public void init(List<MQListener> listeners, MQProperties properties,
                             MQEventSerialization serialization, MQEventStorer storer) {
                throw new IllegalStateException("broker unavailable");
            }
        };
        MQListenerRegistrar registrar = registrar(Collections.singletonList(failing));

        assertThrows(ApplicationContextException.class,
                () -> registrar.onContextRefreshed(event()));
    }

    @Test
    void destroysClientsInReverseOrderOnlyOnce() throws Exception {
        List<String> closed = new ArrayList<>();
        MQListenerRegistrar registrar = registrar(Arrays.asList(
                new StubClient("first", closed), new StubClient("second", closed)));

        registrar.destroy();
        registrar.destroy();

        assertEquals(Arrays.asList("second", "first"), closed);
    }

    private MQListenerRegistrar registrar(List<MQClient> clients) {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("failing");
        MQListenerBeanPostProcessor processor = new MQListenerBeanPostProcessor(properties);
        @SuppressWarnings("unchecked")
        ObjectProvider<MQEventStorer<?>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new MQListenerRegistrar(processor, clients, properties, new Serialization(), provider);
    }

    private ContextRefreshedEvent event() {
        return new ContextRefreshedEvent(new GenericApplicationContext());
    }

    private static class StubClient implements MQClient {
        private final String impl;
        private final List<String> closed;
        private final AtomicInteger closeCount = new AtomicInteger();

        private StubClient(String impl, List<String> closed) {
            this.impl = impl;
            this.closed = closed;
        }

        @Override public String impl() { return impl; }
        @Override public Consumer<MQEvent> initProducer(MQProperties properties) { return null; }
        @Override public boolean initConsumer(MQListener listener, MQProperties properties) { return true; }
        @Override public void close() {
            if (closeCount.compareAndSet(0, 1) && closed != null) {
                closed.add(impl);
            }
        }
    }

    private static final class Serialization implements MQEventSerialization {
        @Override @SuppressWarnings("unchecked") public <T> T serialize(Object event) { return (T) "{}"; }
        @Override public <S, T> T deserialize(S value, Class<T> type) { return null; }
    }
}
