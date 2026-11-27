/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 通过 <code>mock()</code>、<code>event.setTopic()</code>、<code>event.setMsgId()</code> 验证 <code>OnsPublishSerialization</code> 的运行行为。
 */
class OnsPublishSerializationTest {

    /**
     * 验证<code>publishFailure</code>为空，<code>serializations.get()</code> 等于 <code>1</code>。
     */
    @Test
    void successfulNativeSendUsesOneSerializationForPayloadAndLog() {
        Producer producer = mock(Producer.class);
        AtomicInteger serializations = new AtomicInteger();
        String payload = "{\"order\":\"已下单\"}";
        try (OnsMQClient client = new OnsMQClient(producer, new OnsProperties()) {
            @Override
            public MQEventSerialization serialization() {
                return new MQEventSerialization() {
                    @Override
                    public <S, T> T deserialize(S source, Class<T> type) {
                        return type.cast(new MQEvent());
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T serialize(Object event) {
                        if (serializations.incrementAndGet() != 1) {
                            throw new IllegalStateException("serialization is unavailable after native send");
                        }
                        return (T) payload;
                    }
                };
            }
        }) {
            MQEvent event = new MQEvent();
            event.setTopic("orders");
            event.setMsgId("stable-id");

            RuntimeException publishFailure = null;
            try {
                client.initProducer(new MQProperties()).accept(event);
            } catch (RuntimeException exception) {
                publishFailure = exception;
            }

            ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
            verify(producer).send(sent.capture());
            assertEquals(payload, new String(sent.getValue().getBody(), StandardCharsets.UTF_8));
            assertNull(publishFailure, "Successful native send must complete even when a second serialization would fail");
            assertEquals(1, serializations.get());
        }
    }
}
