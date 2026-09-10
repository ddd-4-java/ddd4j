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
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.nullable;

class ActiveMQAdapterContractTest {

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
    void shouldCloseConsumerSessionAndConnectionInReverseOrderOnlyOnce() throws Exception {
        ActiveMQConnectionFactory factory = mock(ActiveMQConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Session session = mock(Session.class);
        jakarta.jms.Topic destination = mock(jakarta.jms.Topic.class);
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
