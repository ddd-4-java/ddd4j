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

import java.util.Collections;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitMQAdapterContractTest {

    @Test
    void shouldPreferStableMessageIdAndNackForRedelivery() throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId("native-id")
                .headers(Collections.singletonMap(MessageHeaders.HEADER_MESSAGE_ID, "stable-id"))
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
        client.init(java.util.List.of(), properties, serialization, null);
        MQEvent event = new MQEvent();
        event.setMsgId("message-1");
        event.setTopic("orders");

        event.publish();

        verify(channel).confirmSelect();
        org.mockito.ArgumentCaptor<AMQP.BasicProperties> captor =
                org.mockito.ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(channel).basicPublish(org.mockito.ArgumentMatchers.eq("events"),
                org.mockito.ArgumentMatchers.anyString(), captor.capture(), org.mockito.ArgumentMatchers.any());
        assertEquals(2, captor.getValue().getDeliveryMode());
        verify(channel).waitForConfirmsOrDie(properties.getPublisherConfirmTimeoutMillis());
    }
}
