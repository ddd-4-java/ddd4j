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
package io.ddd4j.mq.activemq;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import jakarta.jms.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import java.lang.IllegalStateException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * ActiveMQ 因果头、消息读取与连接生命周期三线契约。
 */
class ActiveMQAdapterContractTest {

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>factory.createConnection()</code>\u3001<code>connection.createSession()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void shouldPublishCausalHeadersAsNativeJmsProperties() throws Exception {
        ActiveMQConnectionFactory factory = mock(ActiveMQConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Session session = mock(Session.class);
        Topic destination = mock(Topic.class);
        MessageProducer producer = mock(MessageProducer.class);
        BytesMessage message = mock(BytesMessage.class);
        when(factory.createConnection()).thenReturn(connection);
        when(connection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(session);
        when(session.createTopic(org.mockito.ArgumentMatchers.anyString())).thenReturn(destination);
        when(session.createProducer(destination)).thenReturn(producer);
        when(session.createBytesMessage()).thenReturn(message);

        MQEvent event = new MQEvent();
        event.setMsgId("message-1");
        event.setNamespace("ddd4j");
        event.setTopic("orders");
        event.setCorrelationId("correlation-1");
        event.setCausationId("cause-1");

        ActiveMQClient client = new ActiveMQClient(factory) {
            @Override
            public MQEventSerialization serialization() {
                return new JsonMQEventSerialization();
            }
        };
        try {
            client.initProducer(new MQProperties()).accept(event);
            verify(message).setStringProperty(
                    ActiveMQClient.jmsProperty(MQDeliveryHeaders.CORRELATION_ID), "correlation-1");
            verify(message).setStringProperty(
                    ActiveMQClient.jmsProperty(MQDeliveryHeaders.CAUSATION_ID), "cause-1");
            verify(message).setJMSCorrelationID("correlation-1");
            verify(producer).send(message);
        } finally {
            client.close();
        }
    }

    /**
     * \u9a8c\u8bc1<code>factory.toURI().toString().contains("localhost")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void shouldConstructArtemisFactoryWithoutContainer() throws Exception {
        try (ActiveMQConnectionFactory factory =
                     new ActiveMQConnectionFactory("tcp://localhost:61616")) {
            assertTrue(factory.toURI().toString().contains("localhost"));
        }
    }

    /**
     * \u9a8c\u8bc1<code>ActiveMQClient.messageId(message)</code> \u7b49\u4e8e <code>"test-id"</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void shouldReadMessageIdAndRequeueOnNack() throws Exception {
        Message message = mock(Message.class);
        Session session = mock(Session.class);
        String sanitizedKey = ActiveMQClient.jmsProperty(MessageHeaders.HEADER_MESSAGE_ID);
        when(message.getStringProperty(sanitizedKey)).thenReturn("test-id");

        assertEquals("test-id", ActiveMQClient.messageId(message));

        ActiveMQAcknowledgment acknowledgment = new ActiveMQAcknowledgment(session, message, 7L, "test-id", null);
        acknowledgment.nack(true);

        verify(session).recover();
        assertTrue(acknowledgment.isAcknowledged());
    }

    /**
     * \u9a8c\u8bc1<code>ActiveMQClient.messageId(message)</code> \u7b49\u4e8e <code>"legacy-id"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void shouldFallbackToLegacyWhenPrimaryMissing() throws Exception {
        Message message = mock(Message.class);
        String sanitizedKey = ActiveMQClient.jmsProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
        when(message.getStringProperty(sanitizedKey)).thenReturn("legacy-id");

        assertEquals("legacy-id", ActiveMQClient.messageId(message));
    }

    /**
     * \u9a8c\u8bc1<code>client.initProducer(new MQProperties())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getCause()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>startFailure</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void shouldCloseConnectionWhenStartFails() throws Exception {
        ActiveMQConnectionFactory factory = mock(ActiveMQConnectionFactory.class);
        Connection connection = mock(Connection.class);
        JMSException startFailure = new JMSException("start failed");
        when(factory.createConnection()).thenReturn(connection);
        doThrow(startFailure).when(connection).start();

        ActiveMQClient client = new ActiveMQClient(factory);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.initProducer(new MQProperties()));

        assertSame(startFailure, failure.getCause());
        verify(connection).close();
        client.close();
        verify(connection, times(1)).close();
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>factory.createConnection()</code>\u3001<code>connection.createSession()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void shouldCloseConsumerSessionAndConnectionInReverseOrderOnlyOnce() throws Exception {
        ActiveMQConnectionFactory factory = mock(ActiveMQConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Session session = mock(Session.class);
        Topic destination = mock(Topic.class);
        MessageConsumer consumer = mock(MessageConsumer.class);
        when(factory.createConnection()).thenReturn(connection);
        when(connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)).thenReturn(session);
        when(session.createTopic("events")).thenReturn(destination);
        when(session.createConsumer(org.mockito.ArgumentMatchers.eq(destination), nullable(String.class)))
                .thenReturn(consumer);

        ActiveMQClient client = new ActiveMQClient(factory);
        MQListener listener = MQListener.builder()
                .bean(this)
                .method(getClass().getDeclaredMethod("listenerMethod"))
                .group("group")
                .namespace("")
                .topic("events")
                .tags("")
                .supports(java.util.Collections.emptyList())
                .separator("-")
                .build();
        client.initConsumer(listener, new MQProperties());

        client.close();
        client.close();

        org.mockito.InOrder order = inOrder(consumer, session, connection);
        order.verify(consumer).close();
        order.verify(session).close();
        order.verify(connection).close();
    }

    /** \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002 */
    @SuppressWarnings("unused")
    private void listenerMethod() {
    }
}
