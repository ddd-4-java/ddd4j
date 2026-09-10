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
package io.ddd4j.mq.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Return;
import com.rabbitmq.client.ReturnCallback;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitMQAdapterContractTest {

    @Test
    void shouldPreferStableMessageIdAndNackForRedelivery() throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId("native-id")
                .headers(Collections.<String, Object>singletonMap(MessageHeaders.HEADER_MESSAGE_ID, "stable-id"))
                .build();
        Channel channel = mock(Channel.class);
        RabbitAcknowledgment acknowledgment = new RabbitAcknowledgment(channel, 9L, "stable-id", null);

        assertEquals("stable-id", RabbitMQClient.messageId(properties));
        acknowledgment.nack(true);

        verify(channel).basicNack(9L, false, true);
        assertTrue(acknowledgment.isAcknowledged());
    }

    @Test
    void shouldPublishPersistentMessageAndWaitForBrokerConfirm() throws Exception {
        Channel channel = mock(Channel.class);
        Connection connection = mock(Connection.class);
        when(connection.createChannel()).thenReturn(channel);
        RabbitMQProperties properties = new RabbitMQProperties();
        properties.setEnabled(true);
        properties.setBroker("rabbit");
        properties.setExchange("events");
        properties.setDurable(true);
        RabbitMQClient client = new RabbitMQClient(connection);
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override public <S, T> T deserialize(S src, Class<T> dist) { return null; }
            @Override @SuppressWarnings("unchecked") public <T> T serialize(Object src) { return (T) "{}"; }
        };
        client.init(Collections.<io.ddd4j.mq.listener.MQListener>emptyList(), properties, serialization, null);
        MQEvent event = new MQEvent();
        event.setMsgId("message-1");
        event.setTopic("orders");

        event.publish();

        // ThreadLocal channel: confirmSelect called once during channel init
        verify(channel).confirmSelect();
        // addReturnListener called per-publish (ThreadLocal pattern)
        verify(channel).addReturnListener(any(ReturnCallback.class));
        org.mockito.ArgumentCaptor<AMQP.BasicProperties> captor =
                org.mockito.ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(channel).basicPublish(eq("events"), anyString(), eq(true), captor.capture(), any());
        assertEquals(2, captor.getValue().getDeliveryMode());
        verify(channel).waitForConfirmsOrDie(properties.getPublisherConfirmTimeoutMillis());
    }

    @Test
    void shouldRejectMessageReturnedAsUnroutable() throws Exception {
        Channel channel = mock(Channel.class);
        Connection connection = mock(Connection.class);
        when(connection.createChannel()).thenReturn(channel);
        AtomicReference<ReturnCallback> returnCallback = new AtomicReference<>();
        doAnswer(invocation -> {
            returnCallback.set(invocation.getArgument(0));
            return null;
        }).when(channel).addReturnListener(any(ReturnCallback.class));
        doAnswer(invocation -> {
            ReturnCallback callback = returnCallback.get();
            if (callback != null) {
                AMQP.BasicProperties returnedProperties = new AMQP.BasicProperties.Builder()
                        .messageId("message-unroutable")
                        .build();
                callback.handle(new Return(312, "NO_ROUTE", "events", "orders", returnedProperties, new byte[0]));
            }
            return null;
        }).when(channel).waitForConfirmsOrDie(5000L);
        RabbitMQProperties properties = new RabbitMQProperties();
        properties.setEnabled(true);
        properties.setBroker("rabbit");
        properties.setExchange("events");
        RabbitMQClient client = new RabbitMQClient(connection);
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override public <S, T> T deserialize(S src, Class<T> dist) { return null; }
            @Override @SuppressWarnings("unchecked") public <T> T serialize(Object src) { return (T) "{}"; }
        };
        client.init(Collections.<io.ddd4j.mq.listener.MQListener>emptyList(), properties, serialization, null);
        MQEvent event = new MQEvent();
        event.setMsgId("message-unroutable");
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);
    }
}
