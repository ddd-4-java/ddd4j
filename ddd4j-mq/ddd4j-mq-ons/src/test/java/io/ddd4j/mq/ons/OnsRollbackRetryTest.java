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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** \u9a8c\u8bc1 <code>OnsRollbackRetry</code> \u5728 <code>requiredConsumerFailureAllowsRetry</code>\u3001<code>rolledBackPublisher</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class OnsRollbackRetryTest {

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>fixture.failRequiredConsumer()</code>\u3001<code>fixture.startAgain()</code>\u3001<code>newEvent()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>rolledBackPublisher.accept(newEvent())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"ONS producer is no longer active"</code>\u3002 */
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
     * \u8bfb\u53d6 <code>event</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>event</code>
     */
    private static MQEvent newEvent() {
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setMsgId("stable-id");
        return event;
    }

    /** \u5b9e\u73b0 <code>AutoCloseable</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>failRequiredConsumer()</code>\u3001<code>startAgain()</code>\u3001<code>close()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class RetryFixture implements AutoCloseable {
        /** \u7528\u4e8e\u9694\u79bb <code>Producer</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
        private final Producer firstProducer = mock(Producer.class);
        /** \u7528\u4e8e\u9694\u79bb <code>Producer</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
        private final Producer replacementProducer = mock(Producer.class);
        /** \u7528\u4e8e\u9694\u79bb <code>com.aliyun.openservices.ons.api.Consumer</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
        private final com.aliyun.openservices.ons.api.Consumer failingConsumer =
                mock(com.aliyun.openservices.ons.api.Consumer.class);
        /** \u7528\u4e8e\u9694\u79bb <code>com.aliyun.openservices.ons.api.Consumer</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
        private final com.aliyun.openservices.ons.api.Consumer replacementConsumer =
                mock(com.aliyun.openservices.ons.api.Consumer.class);
        /** \u4f9b\u65ad\u8a00\u521b\u5efa\u6b21\u6570\u548c\u5173\u95ed\u65f6\u673a\u4f7f\u7528\u7684\u539f\u751f\u751f\u4ea7\u8005\u96c6\u5408\u3002 */
        private final Deque<Producer> producers = new ArrayDeque<>(Arrays.asList(firstProducer, replacementProducer));
        /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u542f\u52a8\u3001\u6ce8\u518c\u6216\u5173\u95ed\u884c\u4e3a\u7684\u6d88\u8d39\u8005\u96c6\u5408\u3002 */
        private final Deque<com.aliyun.openservices.ons.api.Consumer> consumers =
                new ArrayDeque<>(Arrays.asList(failingConsumer, replacementConsumer));
        /** \u4fdd\u5b58\u5f53\u524d\u6d4b\u8bd5\u6ce8\u518c\u548c\u6062\u590d\u7684\u53d1\u5e03\u5668\u96c6\u5408\u3002 */
        private final Deque<Consumer<MQEvent>> publishers = new ArrayDeque<>();
        /** \u88c5\u914d\u9694\u79bb\u6d4b\u8bd5\u8fd0\u884c\u73af\u5883\u65f6\u4f7f\u7528\u7684\u914d\u7f6e\u5c5e\u6027\u3002 */
        private final MQProperties properties = new MQProperties();
        /** \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u76d1\u542c\u5668\u96c6\u5408\u3002 */
        private final List<MQListener> listeners = Collections.singletonList(
                MQListener.builder().group("group").topic("orders").tags("*").required(true).build());
        /** \u6ce8\u5165\u6d4b\u8bd5\u53d1\u5e03\u5668\u524d\u4fdd\u7559\u7684\u53d1\u5e03\u5668\u6620\u5c04\u3002 */
        private final Map<String, Consumer<MQEvent>> previousPublishers = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
        /** \u6d4b\u8bd5\u8986\u76d6\u6846\u67b6\u914d\u7f6e\u524d\u4fdd\u7559\u7684\u5c5e\u6027\u3002 */
        private final MQProperties previousProperties = BaseContext.get(MQEvent.MQ_PROPERTIES);
        /** \u66ff\u6362\u6d88\u606f\u7f16\u7801\u7ec4\u4ef6\u524d\u4fdd\u7559\u7684\u7f16\u7801\u914d\u7f6e\u3002 */
        private final MQEventSerialization previousSerialization = BaseContext.get(MQClient.MQ_SERIALIZATION);
        /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
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

        /** \u7528\u4e8e\u6309\u6d4b\u8bd5\u534f\u8bae\u7f16\u89e3\u7801\u6d88\u606f\u8d1f\u8f7d\u7684\u9002\u914d\u5668\u3002 */
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

        /** \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>RetryFixture</code> \u5b9e\u4f8b\u3002 */
        private RetryFixture() {
            doThrow(new IllegalStateException("required consumer startup failed")).when(failingConsumer).start();
            properties.setEnabled(true);
            properties.setBroker("ons");
        }

        /** \u8c03\u7528 <code>times()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        private void failRequiredConsumer() {
            assertThrows(MQInitializationException.class, this::startAgain);
            verify(firstProducer, times(1)).start();
            verify(firstProducer, times(1)).shutdown();
            verify(failingConsumer, times(1)).shutdown();
        }

        /** \u8c03\u7528 <code>client.init()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        private void startAgain() {
            client.init(listeners, properties, serialization, null);
        }

        /** {@inheritDoc} */
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

        /**
         * \u8c03\u7528 <code>BaseContext.remove()</code>\u3001<code>BaseContext.inject()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         */
        private static void restore(String key, Object value) {
            if (Objects.isNull(value)) {
                BaseContext.remove(key);
            } else {
                BaseContext.inject(key, value);
            }
        }
    }
}
