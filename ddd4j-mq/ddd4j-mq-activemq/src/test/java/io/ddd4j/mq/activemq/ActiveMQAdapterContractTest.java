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
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

class ActiveMQAdapterContractTest {

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

    @Test
    void shouldConstructArtemisFactoryWithoutContainer() throws Exception {
        try (ActiveMQConnectionFactory factory =
                     new ActiveMQConnectionFactory("tcp://localhost:61616")) {
            assertTrue(factory.toURI().toString().contains("localhost"));
        }
    }

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

    @Test
    void shouldFallbackToLegacyWhenPrimaryMissing() throws Exception {
        Message message = mock(Message.class);
        String sanitizedKey = ActiveMQClient.jmsProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
        when(message.getStringProperty(sanitizedKey)).thenReturn("legacy-id");

        assertEquals("legacy-id", ActiveMQClient.messageId(message));
    }

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

    @Test
    void shouldCloseConsumerSessionAndConnectionInReverseOrderOnlyOnce() throws Exception {
        ActiveMQConnectionFactory factory = mock(ActiveMQConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Session session = mock(Session.class);
        javax.jms.Topic destination = mock(javax.jms.Topic.class);
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

    @SuppressWarnings("unused")
    private void listenerMethod() {
    }
}
