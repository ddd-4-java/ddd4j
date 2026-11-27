/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.lifecycle.MQInitializationException;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 <code>OnsRollbackRetry</code> 在 <code>requiredConsumerFailureAllowsRetry</code>、<code>rolledBackPublisher</code> 等场景中的行为。
 */
class OnsRollbackRetryTest {

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
     * 在测试框架中执行 <code>fixture.failRequiredConsumer()</code>、<code>fixture.startAgain()</code>、<code>newEvent()</code>，验证该场景。
     */
    @Test
    void requiredConsumerFailureAllowsRetryWithNewOwnedProducer() {
        try (RetryFixture fixture = new RetryFixture()) {
            fixture.failRequiredConsumer();
            fixture.startAgain();

            newEvent().publish();

            verify(fixture.firstProducer, never()).send(any(Message.class));
            verify(fixture.replacementProducer, times(1)).start();
            verify(fixture.replacementProducer, times(1)).send(any(Message.class));
            verify(fixture.firstProducer, times(1)).shutdown();
        }
    }

    /**
     * 验证<code>rolledBackPublisher.accept(newEvent())</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage()</code> 等于 <code>"ONS producer is no longer active"</code>。
     */
    @Test
    void rolledBackPublisherRejectsSendsAfterSuccessfulRetry() {
        try (RetryFixture fixture = new RetryFixture()) {
            fixture.failRequiredConsumer();
            Consumer<MQEvent> rolledBackPublisher = fixture.publishers.getFirst();
            fixture.startAgain();

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> rolledBackPublisher.accept(newEvent()));
            assertEquals("ONS producer is no longer active", failure.getMessage());
            verify(fixture.firstProducer, never()).send(any(Message.class));
        }
    }

    /**
     * 实现 <code>AutoCloseable</code>，供测试调用 <code>failRequiredConsumer()</code>、<code>startAgain()</code>、<code>close()</code> 并观察结果。
     */
    private static final class RetryFixture implements AutoCloseable {
        /**
         * 用于隔离 <code>Producer</code> 调用并记录交互的测试替身。
         */
        private final Producer firstProducer = mock(Producer.class);
        /**
         * 用于隔离 <code>Producer</code> 调用并记录交互的测试替身。
         */
        private final Producer replacementProducer = mock(Producer.class);
        /**
         * 用于隔离 <code>com.aliyun.openservices.ons.api.Consumer</code> 调用并记录交互的测试替身。
         */
        private final com.aliyun.openservices.ons.api.Consumer failingConsumer =
                mock(com.aliyun.openservices.ons.api.Consumer.class);
        /**
         * 用于隔离 <code>com.aliyun.openservices.ons.api.Consumer</code> 调用并记录交互的测试替身。
         */
        private final com.aliyun.openservices.ons.api.Consumer replacementConsumer =
                mock(com.aliyun.openservices.ons.api.Consumer.class);
        /**
         * 供断言创建次数和关闭时机使用的原生生产者集合。
         */
        private final Deque<Producer> producers = new ArrayDeque<>(Arrays.asList(firstProducer, replacementProducer));
        /**
         * 供测试验证启动、注册或关闭行为的消费者集合。
         */
        private final Deque<com.aliyun.openservices.ons.api.Consumer> consumers =
                new ArrayDeque<>(Arrays.asList(failingConsumer, replacementConsumer));
        /**
         * 保存当前测试注册和恢复的发布器集合。
         */
        private final Deque<Consumer<MQEvent>> publishers = new ArrayDeque<>();
        /**
         * 装配隔离测试运行环境时使用的配置属性。
         */
        private final MQProperties properties = new MQProperties();
        /**
         * 当前测试登记的事件或消息监听器集合。
         */
        private final List<MQListener> listeners = Collections.singletonList(
                MQListener.builder().group("group").topic("orders").tags("*").required(true).build());
        /**
         * 注入测试发布器前保留的发布器映射。
         */
        private final Map<String, Consumer<MQEvent>> previousPublishers = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
        /**
         * 测试覆盖框架配置前保留的属性。
         */
        private final MQProperties previousProperties = BaseContext.get(MQEvent.MQ_PROPERTIES);
        /**
         * 替换消息编码组件前保留的编码配置。
         */
        private final MQEventSerialization previousSerialization = BaseContext.get(MQClient.MQ_SERIALIZATION);
        /**
         * 发送与接收测试请求的客户端。
         */
        private final OnsMQClient client = new OnsMQClient(new OnsProperties() {
            @Override
            public Properties sessionProperties(String groupName) {
                return new Properties();
            }
        }) {
            @Override
            Producer createProducer(Properties sessionProperties) {
                return producers.removeFirst();
            }

            @Override
            com.aliyun.openservices.ons.api.Consumer createConsumer(Properties sessionProperties) {
                return consumers.removeFirst();
            }

            @Override
            public Consumer<MQEvent> initProducer(MQProperties configuration) {
                Consumer<MQEvent> publisher = super.initProducer(configuration);
                publishers.addLast(publisher);
                return publisher;
            }
        };

        /**
         * 用于按测试协议编解码消息负载的适配器。
         */
        private final MQEventSerialization serialization = new MQEventSerialization() {
            @Override
            public <S, T> T deserialize(S source, Class<T> type) {
                return type.cast(new MQEvent());
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }
        };

        /**
         * 构造供当前测试使用的 <code>RetryFixture</code> 实例。
         */
        private RetryFixture() {
            doThrow(new IllegalStateException("required consumer startup failed")).when(failingConsumer).start();
            properties.setEnabled(true);
            properties.setBroker("ons");
        }

        /**
         * 调用 <code>BaseContext.remove()</code>、<code>BaseContext.inject()</code> 执行当前夹具操作。
         *
         * @param key   用于在测试上下文或缓存中索引数据的键
         * @param value 当前用例传入的值
         */
        private static void restore(String key, Object value) {
            if (Objects.isNull(value)) {
                BaseContext.remove(key);
            } else {
                BaseContext.inject(key, value);
            }
        }

        /**
         * 调用 <code>times()</code> 执行当前夹具操作。
         */
        private void failRequiredConsumer() {
            assertThrows(MQInitializationException.class, this::startAgain);
            verify(firstProducer, times(1)).start();
            verify(firstProducer, times(1)).shutdown();
            verify(failingConsumer, times(1)).shutdown();
        }

        /**
         * 调用 <code>client.init()</code> 执行当前夹具操作。
         */
        private void startAgain() {
            client.init(listeners, properties, serialization, null);
        }

        /**
         * 结束当前组件持有的资源。
         *
         * <p>测试夹具会调用 <code>client.close()</code>、<code>restore(MQEvent.MQ_EVENT_PUBLISHER, previousPublishers)</code>。</p>
         */
        @Override
        public void close() {
            try {
                client.close();
            } finally {
                restore(MQEvent.MQ_EVENT_PUBLISHER, previousPublishers);
                restore(MQEvent.MQ_PROPERTIES, previousProperties);
                restore(MQClient.MQ_SERIALIZATION, previousSerialization);
            }
        }
    }
}
