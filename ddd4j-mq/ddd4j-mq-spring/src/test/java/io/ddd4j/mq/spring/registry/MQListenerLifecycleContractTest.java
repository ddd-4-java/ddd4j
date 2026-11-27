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

/**
 * 通过 <code>registrar()</code>、<code>Collections.singletonList()</code>、<code>registrar.onContextRefreshed()</code> 验证 <code>MQListenerLifecycleContract</code> 的运行行为。
 */
class MQListenerLifecycleContractTest {

    /**
     * 验证<code>registrar.onContextRefreshed(event())</code> 抛出 <code>ApplicationContextException.class</code>。
     */
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
     * 验证<code>closed</code> 等于 <code>Arrays.asList("second", "first")</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 创建 <code>new MQListenerRegistrar</code> 供当前测试路径调用。
     *
     * @param clients 用于构造本次返回结果的 <code>List&lt;MQClient&gt;</code> 输入
     * @return 当前测试使用的结果，取自 <code>new MQListenerRegistrar(processor, clients, properties, new Serialization(), provider)</code>
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
     * 创建 <code>new ContextRefreshedEvent</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new ContextRefreshedEvent(new GenericApplicationContext())</code>
     */
    private ContextRefreshedEvent event() {
        return new ContextRefreshedEvent(new GenericApplicationContext());
    }

    /**
     * 实现 <code>MQClient</code>，供测试调用 <code>impl()</code>、<code>initProducer()</code>、<code>initConsumer()</code> 并观察结果。
     */
    private static class StubClient implements MQClient {
        /**
         * 当前测试使用并观察的具体接口实现。
         */
        private final String impl;
        /**
         * 用于观察本次测试的资源是否已经关闭。
         */
        private final List<String> closed;
        /**
         * 在测试线程之间共享当前操作次数或更新数量的计数器。
         */
        private final AtomicInteger closeCount = new AtomicInteger();

        /**
         * 用给定值初始化 <code>StubClient</code> 实例。
         *
         * @param impl   <code>impl</code> 字段
         * @param closed 用于观察本次测试的资源是否已经关闭
         */
        private StubClient(String impl, List<String> closed) {
            this.impl = impl;
            this.closed = closed;
        }

        /**
         * 返回此消息适配器在 DDD4J 中登记的实现名。
         *
         * @return 实际返回<code>impl</code>，供调用方取得当前值
         */
        @Override
        public String impl() {
            return impl;
        }

        /**
         * 准备消息生产者并返回可发布事件的入口。
         *
         * @param properties MQ 配置
         * @return 此实现返回<code>null</code>
         */
        @Override
        public Consumer<MQEvent> initProducer(MQProperties properties) {
            return null;
        }

        /**
         * 为监听器建立消息消费所需的订阅和客户端资源。
         *
         * @param listener   监听器定义
         * @param properties MQ 配置
         * @return 此实现返回<code>true</code>
         */
        @Override
        public boolean initConsumer(MQListener listener, MQProperties properties) {
            return true;
        }

        /**
         * 调用 <code>closeCount.compareAndSet()</code>、<code>closed.add()</code> 执行当前夹具操作。
         */
        @Override
        public void close() {
            if (closeCount.compareAndSet(0, 1) && closed != null) {
                closed.add(impl);
            }
        }
    }

    /**
     * 实现 <code>MQEventSerialization</code>，供测试调用 <code>serialize()</code>、<code>deserialize()</code> 并观察结果。
     */
    private static final class Serialization implements MQEventSerialization {
        /**
         * 将传入消息编码为发布通道使用的格式。
         *
         * @param <T>   序列化结果类型
         * @param event 当前发布、消费或处理的事件
         * @return 实际返回<code>(T)"{}"</code>，供调用方取得当前值
         */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T serialize(Object event) {
            return (T) "{}";
        }

        /**
         * 从消息通道载荷还原事件。
         *
         * @param <S>   源类型
         * @param <T>   目标类型
         * @param value 要写入或作为比较条件传入的值
         * @param type  当前操作用于选择实现或转换结果的类型
         * @return 此实现返回<code>null</code>
         */
        @Override
        public <S, T> T deserialize(S value, Class<T> type) {
            return null;
        }
    }
}
