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

/** \u9a8c\u8bc1 <code>MQClientInitializationContract</code> \u5728 <code>producerFailure</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class MQClientInitializationContractTest {

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664<code>BaseContext</code>\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>built.isRequired()</code>\u4e3a\u771f\uff0c<code>legacy.isRequired()</code>\u4e3a\u771f\uff0c<code>MQListener.builder().required(false).build().isRequired()</code>\u4e3a\u5047\u3002 */
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
     * \u9a8c\u8bc1<code>client.init(Collections.singletonList(listener(true, "required")), properties(), serialization(), null)</code> \u629b\u51fa <code>MQInitializationException.class</code>\uff0c<code>exception.topic()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>BaseContext.get(MQClient.MQ_SERIALIZATION)</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u6216\u7b49\u5f85\u6d4b\u8bd5\u7ed3\u679c\u5931\u8d25
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
     * \u9a8c\u8bc1<code>client.init(Collections.singletonList(listener(true, "required")), properties(), serialization(), null)</code> \u629b\u51fa <code>MQInitializationException.class</code>\uff0c<code>publishers.get("test")</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previousPublisher</code>\uff0c<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>publishers</code>\u3002
     * @throws Exception \u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u6216\u7b49\u5f85\u6d4b\u8bd5\u7ed3\u679c\u5931\u8d25
     */
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

    /** \u9a8c\u8bc1<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>\u975e\u7a7a\uff0c<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void closingClientUnregistersItsPublisher() {
        TestClient client = new TestClient();
        client.init(Collections.emptyList(), properties(), serialization(), null);
        assertNotNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));

        client.close();

        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
    }

    /** \u9a8c\u8bc1<code>publishers.get("test")</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>activePublisher</code>\uff0c<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>\u4e3a\u7a7a\u3002 */
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
     * \u9a8c\u8bc1<code>failing.init(Collections.singletonList(listener(true, "required")), properties(), serialization(), null)</code> \u629b\u51fa <code>MQInitializationException.class</code>\uff0c<code>BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER)</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u6216\u7b49\u5f85\u6d4b\u8bd5\u7ed3\u679c\u5931\u8d25
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

    /** \u9a8c\u8bc1<code>client.init(Collections.emptyList(), properties(), serialization(), newStorer)</code> \u629b\u51fa <code>MQInitializationException.class</code>\uff0c<code>client.closedResources.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>BaseContext.get(MQClient.MQ_STORER)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previousStorer</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>client.attempts.get()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>client.closedResources.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>client.startupStatus().snapshot().failures().get(0).required()</code>\u4e3a\u5047\u3002
     * @throws Exception \u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u6216\u7b49\u5f85\u6d4b\u8bd5\u7ed3\u679c\u5931\u8d25
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
     * \u8bfb\u53d6 <code>properties</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>properties</code>
     */
    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("test");
        return properties;
    }

    /**
     * \u6784\u9020 <code>MQEventSerialization</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQEventSerialization</code> \u7ed3\u679c
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
     * \u8c03\u7528 <code>Handler.class.getMethod()</code>\u3001<code>MQListener.builder()</code>\u3001<code>Collections.singletonList()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param required \u4f20\u7ed9 <code>MQListener.builder()</code> \u7684 <code>boolean</code> \u8f93\u5165
     * @param group \u4f20\u7ed9 <code>MQListener.builder()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQListener</code> \u7ed3\u679c
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private MQListener listener(boolean required, String group) throws Exception {
        Method method = Handler.class.getMethod("handle", MQEvent.class);
        return MQListener.builder().bean(new Handler()).method(method).group(group).namespace("")
                .topic("orders").tags("*").supports(Collections.singletonList("*"))
                .separator(".").required(required).build();
    }

    /** \u63d0\u4f9b <code>handle()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public static final class Handler {
        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void handle(MQEvent event) {
        }
    }

    /** \u5b9e\u73b0 <code>MQClient</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>withProducerFailure()</code>\u3001<code>beforeProducerInit()</code>\u3001<code>beforeConsumerInit()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class TestClient implements MQClient {
        /** \u4fdd\u5b58\u8bf7\u6c42\u3001\u56de\u8c03\u6216\u88ab\u6d4b\u65b9\u6cd5\u8fd4\u56de\u7684\u7ed3\u679c\u3002 */
        private final boolean[] results;
        /** \u63a7\u5236\u5f53\u524d\u6d4b\u8bd5\u4ee3\u7406\u751f\u4ea7\u8005\u662f\u5426\u88c5\u914d\u5931\u8d25\u3002 */
        private boolean failProducer;
        /** \u6d4b\u8bd5\u63a7\u5236\u6d88\u8d39\u8005\u521d\u59cb\u5316\u7ed3\u679c\u7684\u56de\u8c03\u3002 */
        private Runnable consumerInitHook = () -> { };
        /** \u8ddf\u8e2a\u6d4b\u8bd5\u64cd\u4f5c\u7684\u8c03\u7528\u6216\u91cd\u8bd5\u6b21\u6570\u3002 */
        private final AtomicInteger attempts = new AtomicInteger();
        /** \u5728\u6d4b\u8bd5\u7ebf\u7a0b\u4e4b\u95f4\u5171\u4eab\u5f53\u524d\u64cd\u4f5c\u6b21\u6570\u6216\u66f4\u65b0\u6570\u91cf\u7684\u8ba1\u6570\u5668\u3002 */
        private final AtomicInteger closedResources = new AtomicInteger();
        /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
        private final MQClientLifecycle lifecycle = new MQClientLifecycle();
        /** \u4fdd\u5b58\u6d88\u606f\u7ec4\u4ef6\u5f53\u524d\u542f\u52a8\u7ed3\u679c\u7684\u72b6\u6001\u5bf9\u8c61\u3002 */
        private final MQStartupStatus status = new MQStartupStatus("test");

        /**
         * \u8bbe\u7f6e <code>results</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestClient</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param results \u7528\u4e8e\u521d\u59cb\u5316 <code>TestClient</code> \u7684 <code>boolean[]</code> \u503c
         */
        private TestClient(boolean... results) {
            this.results = results;
        }

        /**
         * \u8bfb\u53d6 <code>this</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>this</code>
         */
        private TestClient withProducerFailure() {
            failProducer = true;
            return this;
        }

        /**
         * \u8bfb\u53d6 <code>this</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param hook \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Runnable</code> \u53c2\u6570 <code>hook</code>
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>this</code>
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
         * @param properties MQ 配置
         * @return 发布函数，null 表示不提供发布能力
         */
        @Override
        public Consumer<MQEvent> initProducer(MQProperties properties) {
            lifecycle.register("producer", closedResources::incrementAndGet);
            if (failProducer) {
                throw new IllegalStateException("producer unavailable");
            }
            return event -> {
            };
        }

        /**
         * 为监听器建立消息消费所需的订阅和客户端资源。
         *
         * <p>测试夹具会调用 <code>consumerInitHook.run()</code>、<code>attempts.getAndIncrement()</code>。</p>
         *
         * @param listener 监听器定义
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
