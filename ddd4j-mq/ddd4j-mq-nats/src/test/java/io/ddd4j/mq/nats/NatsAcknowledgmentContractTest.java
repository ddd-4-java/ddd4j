/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.mq.nats;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** \u9a8c\u8bc1 <code>NatsAcknowledgment</code> \u5728<code>nativeHeaders</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class NatsAcknowledgmentContractTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldMapNackToJetStreamNakForRedelivery() {
        Message message = mock(Message.class);
        NatsAcknowledgment acknowledgment = new NatsAcknowledgment(message);

        acknowledgment.nack(true);

        verify(message).nak();
        assertTrue(acknowledgment.isAcknowledged());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>headers.put()</code>\u3001<code>message.getHeaders()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldPreferNativeCorrelationHeaderInAcknowledgment() {
        Message message = mock(Message.class);
        Headers headers = new Headers();
        headers.put(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
        when(message.getHeaders()).thenReturn(headers);
        when(message.getReplyTo()).thenReturn("legacy-reply");

        assertEquals("native-correlation", new NatsAcknowledgment(message).correlationId());

        when(message.getHeaders()).thenReturn(null);
        assertEquals("legacy-reply", new NatsAcknowledgment(message).correlationId());
    }

    /**
     * \u9a8c\u8bc1<code>handler.received</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>event</code>\uff0c<code>handler.received.getCorrelationId()</code> \u7b49\u4e8e <code>"native-correlation"</code>\uff0c<code>handler.received.getTag()</code> \u7b49\u4e8e <code>"priority"</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void nativeHeadersShouldOverridePayloadBeforeListener() throws Exception {
        Connection connection = mock(Connection.class);
        NatsMQClient client = new NatsMQClient(connection);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("nats");
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setCorrelationId("payload-correlation");
        event.setCausationId("payload-causation");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object value) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return type.cast(event);
            }
        }, null);
        CapturingHandler handler = new CapturingHandler();
        Method handlerMethod = CapturingHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(handler, handlerMethod,
                handlerMethod.getAnnotation(MQEventListener.class));
        Message message = mock(Message.class);
        Headers headers = new Headers();
        headers.put(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
        headers.put(MQDeliveryHeaders.CAUSATION_ID, "native-causation");
        headers.put(io.ddd4j.mq.message.MessageHeaders.HEADER_DESTINATION_TAG, "priority");
        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(message.getSubject()).thenReturn("orders");

        Method onMessage = NatsMQClient.class.getDeclaredMethod("onMessage", Message.class, MQListener.class);
        onMessage.setAccessible(true);
        onMessage.invoke(client, message, listener);

        assertSame(event, handler.received);
        assertEquals("native-correlation", handler.received.getCorrelationId());
        assertEquals("native-causation", handler.received.getCausationId());
        assertEquals("priority", handler.received.getTag());
        client.close();
    }

    /** \u5c06 <code>received</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class CapturingHandler {
        /** \u5f53\u524d\u6d4b\u8bd5\u63a5\u6536\u7684\u6570\u636e\uff0c\u4f9b\u65ad\u8a00\u6838\u5bf9\u6295\u9012\u5185\u5bb9\u6216\u6b21\u6570\u3002 */
        private MQEvent received;

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>received = event</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @MQEventListener(topic = "orders", tags = "priority")
        public void handle(MQEvent event) {
            received = event;
        }
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldFailInsteadOfFallingBackToNonDurableCorePublish() throws Exception {
        Connection connection = mock(Connection.class);
        JetStream jetStream = mock(JetStream.class);
        when(connection.jetStream()).thenReturn(jetStream);
        doThrow(new IOException("jetstream unavailable"))
                .when(jetStream).publish(anyString(), any(Headers.class), any(byte[].class));
        NatsMQClient client = new NatsMQClient(connection);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("nats");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return null;
            }
        }, null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);
        verify(connection, never()).publish(anyString(), any(Headers.class), any(byte[].class));

        client.close();
        client.close();
        verify(connection, never()).close();
        verify(connection, never()).drain(any(java.time.Duration.class));
    }
}
