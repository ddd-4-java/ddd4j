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

/** \u901a\u8fc7 <code>registrar()</code>\u3001<code>Collections.singletonList()</code>\u3001<code>registrar.onContextRefreshed()</code> \u9a8c\u8bc1 <code>MQListenerLifecycleContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class MQListenerLifecycleContractTest {

    /** \u9a8c\u8bc1<code>registrar.onContextRefreshed(event())</code> \u629b\u51fa <code>ApplicationContextException.class</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>closed</code> \u7b49\u4e8e <code>Arrays.asList("second", "first")</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void destroysClientsInReverseOrderOnlyOnce() throws Exception {
        List<String> closed = new ArrayList<>();
        MQListenerRegistrar registrar = registrar(Arrays.asList(
                new StubClient("first", closed), new StubClient("second", closed)));

        registrar.destroy();
        registrar.destroy();

        assertEquals(Arrays.asList("second", "first"), closed);
    }

    /**
     * \u521b\u5efa <code>new MQListenerRegistrar</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param clients \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>List&lt;MQClient&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new MQListenerRegistrar(processor, clients, properties, new Serialization(), provider)</code>
     */
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

    /**
     * \u521b\u5efa <code>new ContextRefreshedEvent</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ContextRefreshedEvent(new GenericApplicationContext())</code>
     */
    private ContextRefreshedEvent event() {
        return new ContextRefreshedEvent(new GenericApplicationContext());
    }

    /** \u5b9e\u73b0 <code>MQClient</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>impl()</code>\u3001<code>initProducer()</code>\u3001<code>initConsumer()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static class StubClient implements MQClient {
        /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u89c2\u5bdf\u7684\u5177\u4f53\u63a5\u53e3\u5b9e\u73b0\u3002 */
        private final String impl;
        /** \u7528\u4e8e\u89c2\u5bdf\u672c\u6b21\u6d4b\u8bd5\u7684\u8d44\u6e90\u662f\u5426\u5df2\u7ecf\u5173\u95ed\u3002 */
        private final List<String> closed;
        /** \u5728\u6d4b\u8bd5\u7ebf\u7a0b\u4e4b\u95f4\u5171\u4eab\u5f53\u524d\u64cd\u4f5c\u6b21\u6570\u6216\u66f4\u65b0\u6570\u91cf\u7684\u8ba1\u6570\u5668\u3002 */
        private final AtomicInteger closeCount = new AtomicInteger();

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>StubClient</code> \u5b9e\u4f8b\u3002
         * @param impl <code>impl</code> \u5b57\u6bb5
         * @param closed \u7528\u4e8e\u89c2\u5bdf\u672c\u6b21\u6d4b\u8bd5\u7684\u8d44\u6e90\u662f\u5426\u5df2\u7ecf\u5173\u95ed
         */
        private StubClient(String impl, List<String> closed) {
            this.impl = impl;
            this.closed = closed;
        }

        /** {@inheritDoc} */
        @Override
        public String impl() {
            return impl;
        }

        /** {@inheritDoc} */
        @Override
        public Consumer<MQEvent> initProducer(MQProperties properties) {
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public boolean initConsumer(MQListener listener, MQProperties properties) {
            return true;
        }

        /** \u8c03\u7528 <code>closeCount.compareAndSet()</code>\u3001<code>closed.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        @Override
        public void close() {
            if (closeCount.compareAndSet(0, 1) && closed != null) {
                closed.add(impl);
            }
        }
    }

    /** \u5b9e\u73b0 <code>MQEventSerialization</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>serialize()</code>\u3001<code>deserialize()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class Serialization implements MQEventSerialization {
        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T serialize(Object event) {
            return (T) "{}";
        }

        /** {@inheritDoc} */
        @Override
        public <S, T> T deserialize(S value, Class<T> type) {
            return null;
        }
    }
}
