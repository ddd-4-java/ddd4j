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
package io.ddd4j.mq.pulsar;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PulsarAdapterContractTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void shouldPreferStableMessageIdAndNegativeAcknowledgeOnRetry() {
        Message<byte[]> message = mock(Message.class);
        Consumer<byte[]> consumer = mock(Consumer.class);
        when(message.getProperty(MessageHeaders.HEADER_MESSAGE_ID)).thenReturn("stable-id");
        when(consumer.isConnected()).thenReturn(true);

        assertEquals("stable-id", PulsarMQClient.messageId(message));
        PulsarAcknowledgment acknowledgment = new PulsarAcknowledgment(consumer, message, "stable-id", null);
        acknowledgment.nack(true);

        verify(consumer).negativeAcknowledge(message);
        assertTrue(acknowledgment.isAcknowledged());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPropagateBrokerSendFailureToPublisher() throws Exception {
        PulsarClient pulsarClient = mock(PulsarClient.class);
        ProducerBuilder<byte[]> producerBuilder = mock(ProducerBuilder.class);
        Producer<byte[]> producer = mock(Producer.class);
        TypedMessageBuilder<byte[]> messageBuilder = mock(TypedMessageBuilder.class);
        when(pulsarClient.newProducer(Schema.BYTES)).thenReturn(producerBuilder);
        when(producerBuilder.topic(anyString())).thenReturn(producerBuilder);
        when(producerBuilder.batchingMaxPublishDelay(anyLong(), any())).thenReturn(producerBuilder);
        when(producerBuilder.create()).thenReturn(producer);
        when(producer.newMessage()).thenReturn(messageBuilder);
        when(messageBuilder.value(any(byte[].class))).thenReturn(messageBuilder);
        when(messageBuilder.property(anyString(), anyString())).thenReturn(messageBuilder);
        CompletableFuture<MessageId> failed = new CompletableFuture<>();
        failed.completeExceptionally(new PulsarClientException("broker unavailable"));
        when(messageBuilder.sendAsync()).thenReturn(failed);
        PulsarMQClient client = new PulsarMQClient(pulsarClient, new PulsarProperties());
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("pulsar");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override public <T> T serialize(Object event) { return (T) "{}"; }
            @Override public <S, T> T deserialize(S value, Class<T> type) { return null; }
        }, null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);
    }
}
