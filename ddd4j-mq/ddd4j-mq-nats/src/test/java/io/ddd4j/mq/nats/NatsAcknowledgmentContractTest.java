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

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
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
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NatsAcknowledgmentContractTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void shouldMapNackToJetStreamNakForRedelivery() {
        Message message = mock(Message.class);
        NatsAcknowledgment acknowledgment = new NatsAcknowledgment(message);

        acknowledgment.nack(true);

        verify(message).nak();
        assertTrue(acknowledgment.isAcknowledged());
    }

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
            @Override public <T> T serialize(Object event) { return (T) "{}"; }
            @Override public <S, T> T deserialize(S value, Class<T> type) { return null; }
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
