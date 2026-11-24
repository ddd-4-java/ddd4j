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

class OnsRollbackRetryTest {

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

    private static MQEvent newEvent() {
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setMsgId("stable-id");
        return event;
    }

    private static final class RetryFixture implements AutoCloseable {
        private final Producer firstProducer = mock(Producer.class);
        private final Producer replacementProducer = mock(Producer.class);
        private final com.aliyun.openservices.ons.api.Consumer failingConsumer =
                mock(com.aliyun.openservices.ons.api.Consumer.class);
        private final com.aliyun.openservices.ons.api.Consumer replacementConsumer =
                mock(com.aliyun.openservices.ons.api.Consumer.class);
        private final Deque<Producer> producers = new ArrayDeque<>(Arrays.asList(firstProducer, replacementProducer));
        private final Deque<com.aliyun.openservices.ons.api.Consumer> consumers =
                new ArrayDeque<>(Arrays.asList(failingConsumer, replacementConsumer));
        private final Deque<Consumer<MQEvent>> publishers = new ArrayDeque<>();
        private final MQProperties properties = new MQProperties();
        private final List<MQListener> listeners = Collections.singletonList(
                MQListener.builder().group("group").topic("orders").tags("*").required(true).build());
        private final Map<String, Consumer<MQEvent>> previousPublishers = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
        private final MQProperties previousProperties = BaseContext.get(MQEvent.MQ_PROPERTIES);
        private final MQEventSerialization previousSerialization = BaseContext.get(MQClient.MQ_SERIALIZATION);
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

        private RetryFixture() {
            doThrow(new IllegalStateException("required consumer startup failed")).when(failingConsumer).start();
            properties.setEnabled(true);
            properties.setBroker("ons");
        }

        private void failRequiredConsumer() {
            assertThrows(MQInitializationException.class, this::startAgain);
            verify(firstProducer, times(1)).start();
            verify(firstProducer, times(1)).shutdown();
            verify(failingConsumer, times(1)).shutdown();
        }

        private void startAgain() {
            client.init(listeners, properties, serialization, null);
        }

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

        private static void restore(String key, Object value) {
            if (Objects.isNull(value)) {
                BaseContext.remove(key);
            } else {
                BaseContext.inject(key, value);
            }
        }
    }
}
