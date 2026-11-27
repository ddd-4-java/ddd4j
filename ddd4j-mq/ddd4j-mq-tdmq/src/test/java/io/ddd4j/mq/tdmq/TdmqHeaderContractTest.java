/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.tdmq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>TdmqHeaderContract</code> \u5728 <code>inMemoryRoute</code>\u3001<code>legacyPublisher</code>\u3001<code>injectedMetadataPublisher</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class TdmqHeaderContractTest {

    /**
     * \u6784\u9020 <code>MQEventSerialization</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param payload \u4f20\u7ed9\u88ab\u6d4b\u64cd\u4f5c\u7684\u6d88\u606f\u8d1f\u8f7d
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQEventSerialization</code> \u7ed3\u679c
     */
    private static MQEventSerialization serialization(MQEvent payload) {
        return new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <S, T> T deserialize(S src, Class<T> dist) {
                return (T) payload;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object src) {
                return (T) "{}";
            }
        };
    }

    /**
     * \u9a8c\u8bc1<code>probe.event.getMsgId()</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>probe.event.getCausationId()</code> \u7b49\u4e8e <code>"native-causation"</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void inMemoryRouteShouldRestoreNativeHeadersOverPayloadMirror() throws Exception {
        MQEvent payload = new MQEvent();
        payload.setMsgId("payload-id");
        payload.setCorrelationId("payload-correlation");
        payload.setCausationId("payload-causation");
        BaseContext.inject(MQClient.MQ_SERIALIZATION, serialization(payload));
        TdmqMQClient client = new TdmqMQClient(new TdmqProperties());
        Probe probe = new Probe();
        MQListener listener = new MQListener();
        listener.setBean(probe);
        listener.setMethod(Probe.class.getMethod("onEvent", MQEvent.class));
        listener.setTopic("orders");
        listener.setTags("*");
        listener.setSupports(Arrays.asList("*"));
        try {
            client.initConsumer(listener, new MQProperties());
            MQEvent source = new MQEvent();
            source.setTopic("orders");
            source.setMsgId("stable-id");
            source.setCorrelationId("native-correlation");
            source.setCausationId("native-causation");

            client.initProducer(new MQProperties()).accept(source);

            assertEquals("stable-id", probe.event.getMsgId());
            assertEquals("native-correlation", probe.event.getCorrelationId());
            assertEquals("native-causation", probe.event.getCausationId());
        } finally {
            client.close();
            BaseContext.remove(MQClient.MQ_SERIALIZATION);
        }
    }

    /** \u9a8c\u8bc1<code>client.initProducer(new MQProperties()).accept(source)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>sends.get()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>sends.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void legacyPublisherShouldRejectCausalityItCannotCarry() {
        AtomicInteger sends = new AtomicInteger();
        TdmqMQClient client = new TdmqMQClient(new TdmqProperties());
        client.setBrokerPublisher((topic, tag, tenant, key, payload) -> sends.incrementAndGet());
        BaseContext.inject(MQClient.MQ_SERIALIZATION, serialization(new MQEvent()));
        try {
            MQEvent source = new MQEvent();
            source.setTopic("orders");
            source.setMsgId("stable-id");
            source.setCorrelationId("native-correlation");

            assertThrows(IllegalStateException.class,
                    () -> client.initProducer(new MQProperties()).accept(source));
            assertEquals(0, sends.get());

            source.setCorrelationId(null);
            source.setCausationId("native-causation");
            assertThrows(IllegalStateException.class,
                    () -> client.initProducer(new MQProperties()).accept(source));
            assertEquals(0, sends.get());

            MQEvent withoutCausality = new MQEvent();
            withoutCausality.setTopic("orders");
            withoutCausality.setMsgId("stable-id");
            client.initProducer(new MQProperties()).accept(withoutCausality);
            assertEquals(1, sends.get());
        } finally {
            client.close();
            BaseContext.remove(MQClient.MQ_SERIALIZATION);
        }
    }

    /** \u9a8c\u8bc1<code>publishedHeaders.get().get("ddd4j-message-id")</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>publishedHeaders.get().get("X-Correlation-Id")</code> \u7b49\u4e8e <code>"correlation-id"</code>\uff0c<code>publishedHeaders.get().get("X-Causation-Id")</code> \u7b49\u4e8e <code>"causation-id"</code>\u3002 */
    @Test
    void injectedMetadataPublisherShouldReceiveFrozenHeaders() {
        AtomicReference<Map<String, String>> publishedHeaders = new AtomicReference<>();
        TdmqMQClient.BrokerPublisher publisher = new TdmqMQClient.BrokerPublisher() {
            @Override
            public void publish(String topic, String tag, String tenantId, String key, byte[] payload) {
                throw new AssertionError("metadata path must be used");
            }

            @Override
            public void publish(String topic, String tag, String tenantId, String key,
                                byte[] payload, Map<String, String> headers) {
                publishedHeaders.set(headers);
            }
        };
        TdmqMQClient client = new TdmqMQClient(new TdmqProperties());
        client.setBrokerPublisher(publisher);
        BaseContext.inject(MQClient.MQ_SERIALIZATION, serialization(new MQEvent()));
        try {
            MQEvent source = new MQEvent();
            source.setTopic("orders");
            source.setMsgId("stable-id");
            source.setCorrelationId("correlation-id");
            source.setCausationId("causation-id");
            client.initProducer(new MQProperties()).accept(source);

            assertEquals("stable-id", publishedHeaders.get().get("ddd4j-message-id"));
            assertEquals("correlation-id", publishedHeaders.get().get("X-Correlation-Id"));
            assertEquals("causation-id", publishedHeaders.get().get("X-Causation-Id"));
            assertTrue(new TdmqMQClient.DeliveredMessage("id", "corr", new byte[0],
                    null, ack -> {
            }).headers().isEmpty());
        } finally {
            client.close();
            BaseContext.remove(MQClient.MQ_SERIALIZATION);
        }
    }

    /** \u5c06 <code>event</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static class Probe {
        /** \u7528\u4f5c\u8f93\u5165\u6216\u7528\u4e8e\u6bd4\u8f83\u5b9e\u9645\u53d1\u5e03\u884c\u4e3a\u7684\u6d4b\u8bd5\u4e8b\u4ef6\u3002 */
        private MQEvent event;

        /**
         * \u8bbe\u7f6e<code>event</code> \u5b57\u6bb5\u3002
         * @param event \u8981\u5199\u5165\u7684\u503c
         */
        public void onEvent(MQEvent event) {
            this.event = event;
        }
    }
}
