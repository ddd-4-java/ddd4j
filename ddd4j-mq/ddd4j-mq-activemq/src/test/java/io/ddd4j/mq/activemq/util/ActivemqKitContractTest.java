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
package io.ddd4j.mq.activemq.util;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JMS 目的地和消息提取在三条 JDK 发布线保持相同契约。
 */
class ActivemqKitContractTest {

    @Test
    void resolvesPhysicalDestinationAndJmsKind() throws Exception {
        assertEquals("ddd4j.default.topic", ActivemqKit.resolvePhysical("", "", ""));
        assertEquals("tenant.orders.audit", ActivemqKit.resolvePhysical("tenant", "orders", "audit"));

        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        Topic topic = mock(Topic.class);
        when(session.createQueue("work")).thenReturn(queue);
        when(session.createTopic("news")).thenReturn(topic);
        assertSame(queue, ActivemqKit.createDestination(session, "queue:work"));
        assertSame(topic, ActivemqKit.createDestination(session, "topic:news"));
        assertSame(topic, ActivemqKit.createDestination(session, "news"));
    }

    @Test
    void extractsTextAndBytesPayload() throws Exception {
        TextMessage text = mock(TextMessage.class);
        when(text.getText()).thenReturn("订单");
        assertEquals("订单", ActivemqKit.extractPayload(text));

        BytesMessage bytes = mock(BytesMessage.class);
        byte[] utf8 = "订单".getBytes(StandardCharsets.UTF_8);
        when(bytes.getBodyLength()).thenReturn((long) utf8.length);
        doAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            System.arraycopy(utf8, 0, target, 0, utf8.length);
            return utf8.length;
        }).when(bytes).readBytes(org.mockito.ArgumentMatchers.any(byte[].class));
        assertEquals("订单", ActivemqKit.extractPayload(bytes));
        verify(bytes).reset();
        assertEquals("", ActivemqKit.extractPayload(mock(Message.class)));
    }

    @Test
    void safelyReadsHeadersAndReturnsDefaultsOnJmsErrors() throws Exception {
        Message message = mock(Message.class);
        when(message.getStringProperty("key")).thenReturn("value");
        when(message.getJMSMessageID()).thenReturn("message-1");
        when(message.getJMSCorrelationID()).thenReturn("correlation-1");
        assertEquals("value", ActivemqKit.stringProperty(message, "key"));
        assertEquals("message-1", ActivemqKit.messageIdOf(message));
        assertEquals("correlation-1", ActivemqKit.correlationIdOf(message));
        assertEquals((long) "message-1".hashCode(), ActivemqKit.messageIdHash(message));

        Message missing = mock(Message.class);
        assertEquals(0L, ActivemqKit.messageIdHash(missing));
        Message broken = mock(Message.class);
        when(broken.getStringProperty("key")).thenThrow(new JMSException("read failed"));
        when(broken.getJMSMessageID()).thenThrow(new JMSException("read failed"));
        when(broken.getJMSCorrelationID()).thenThrow(new JMSException("read failed"));
        assertNull(ActivemqKit.stringProperty(broken, "key"));
        assertNull(ActivemqKit.messageIdOf(broken));
        assertNull(ActivemqKit.correlationIdOf(broken));
        assertEquals(0L, ActivemqKit.messageIdHash(broken));
    }
}
