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

/**
 * 验证 <code>TdmqHeaderContract</code> 在 <code>inMemoryRoute</code>、<code>legacyPublisher</code>、<code>injectedMetadataPublisher</code> 等场景中的行为。
 */
class TdmqHeaderContractTest {

    /**
     * 构造 <code>MQEventSerialization</code> 作为当前测试的输入或结果。
     *
     * @param payload 传给被测操作的消息负载
     * @return 提供给后续契约断言的 <code>MQEventSerialization</code> 结果
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
     * 验证<code>probe.event.getMsgId()</code> 等于 <code>"stable-id"</code>，<code>probe.event.getCausationId()</code> 等于 <code>"native-causation"</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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

    /**
     * 验证<code>client.initProducer(new MQProperties()).accept(source)</code> 抛出 <code>IllegalStateException.class</code>，<code>sends.get()</code> 等于 <code>0</code>，<code>sends.get()</code> 等于 <code>1</code>。
     */
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

    /**
     * 验证<code>publishedHeaders.get().get("ddd4j-message-id")</code> 等于 <code>"stable-id"</code>，<code>publishedHeaders.get().get("X-Correlation-Id")</code> 等于 <code>"correlation-id"</code>，<code>publishedHeaders.get().get("X-Causation-Id")</code> 等于 <code>"causation-id"</code>。
     */
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

    /**
     * 将 <code>event</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static class Probe {
        /**
         * 用作输入或用于比较实际发布行为的测试事件。
         */
        private MQEvent event;

        /**
         * 设置<code>event</code> 字段。
         *
         * @param event 要写入的值
         */
        public void onEvent(MQEvent event) {
            this.event = event;
        }
    }
}
