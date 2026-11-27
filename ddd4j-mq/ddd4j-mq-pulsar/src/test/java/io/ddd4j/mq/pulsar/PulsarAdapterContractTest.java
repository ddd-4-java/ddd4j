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
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.message.MessageHeaders;
import org.apache.pulsar.client.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** \u9a8c\u8bc1 <code>PulsarAdapterContract</code> \u5728 <code>doesNotRedeliver</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class PulsarAdapterContractTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>PulsarMQClient.messageId(message)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>acknowledgment.nack(false)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>acknowledgment.reject(false)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
    @Test
    void doesNotRedeliverWhenNonRequeueWasRequested() {
        Message<byte[]> message = mock(Message.class);
        Consumer<byte[]> consumer = mock(Consumer.class);
        PulsarAcknowledgment acknowledgment = new PulsarAcknowledgment(consumer, message, "stable-id", null);

        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.nack(false));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.reject(false));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.recover(false));
        assertThrows(UnsupportedOperationException.class, acknowledgment::discard);
        assertFalse(acknowledgment.isAcknowledged());
        verifyNoInteractions(consumer);

        acknowledgment.nack(true);
        verify(consumer).negativeAcknowledge(message);
        assertTrue(acknowledgment.isAcknowledged());
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>pulsarClient.newProducer()</code>\u3001<code>producerBuilder.topic()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    @SuppressWarnings("unchecked")
    void taggedEventUsesSubscribedPhysicalTopic() throws Exception {
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
        when(messageBuilder.sendAsync()).thenReturn(CompletableFuture.completedFuture(mock(MessageId.class)));

        PulsarProperties pulsarProperties = new PulsarProperties();
        PulsarMQClient client = new PulsarMQClient(pulsarClient, pulsarProperties);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("pulsar");
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
        event.setTag("priority");
        event.publish();

        verify(producerBuilder).topic(pulsarProperties.physicalTopic("orders", null));
        verify(producerBuilder, never()).topic(pulsarProperties.physicalTopic("orders", "priority"));
        verify(messageBuilder).property(client.tagHeaderKey(), "priority");
        client.close();
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>pulsarClient.newProducer()</code>\u3001<code>producerBuilder.topic()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldPublishCausalityAsNativePulsarProperties() throws Exception {
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
        when(messageBuilder.sendAsync()).thenReturn(CompletableFuture.completedFuture(mock(MessageId.class)));

        PulsarProperties pulsarProperties = new PulsarProperties();
        PulsarMQClient client = new PulsarMQClient(pulsarClient, pulsarProperties);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("pulsar");
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
        MQEvent withoutCausality = new MQEvent();
        withoutCausality.setTopic("orders");
        withoutCausality.setMsgId("plain-id");
        withoutCausality.publish();
        verify(messageBuilder).property(MQDeliveryHeaders.MESSAGE_ID, "plain-id");
        verify(messageBuilder, never()).property(eq(MQDeliveryHeaders.CORRELATION_ID), anyString());
        verify(messageBuilder, never()).property(eq(MQDeliveryHeaders.CAUSATION_ID), anyString());
        clearInvocations(messageBuilder);

        MQEvent correlationOnly = new MQEvent();
        correlationOnly.setTopic("orders");
        correlationOnly.setMsgId("single-id");
        correlationOnly.setCorrelationId("correlation-only");
        correlationOnly.publish();
        verify(messageBuilder).property(MQDeliveryHeaders.CORRELATION_ID, "correlation-only");
        verify(messageBuilder, never()).property(eq(MQDeliveryHeaders.CAUSATION_ID), anyString());
        clearInvocations(messageBuilder);

        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setTag("priority");
        event.setMsgId("stable-id");
        event.setCorrelationId("correlation-id");
        event.setCausationId("causation-id");

        event.publish();

        verify(messageBuilder).property(MQDeliveryHeaders.MESSAGE_ID, "stable-id");
        verify(messageBuilder).property(MQDeliveryHeaders.CORRELATION_ID, "correlation-id");
        verify(messageBuilder).property(MQDeliveryHeaders.CAUSATION_ID, "causation-id");
        // 消费者按逻辑 topic 订阅，tag 只作为消息属性过滤。
        verify(producerBuilder).topic(pulsarProperties.physicalTopic("orders", null));
        verify(producerBuilder, never()).topic(pulsarProperties.physicalTopic("orders", "priority"));
        client.close();
    }

    /**
     * \u9a8c\u8bc1<code>received.get().getCorrelationId()</code> \u7b49\u4e8e <code>"correlation-id"</code>\uff0c<code>acknowledgment.get().correlationId()</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldRestoreCausalityFromNativePulsarProperties() throws Exception {
        PulsarClient pulsarClient = mock(PulsarClient.class);
        ConsumerBuilder<byte[]> consumerBuilder = mock(ConsumerBuilder.class);
        Consumer<byte[]> consumer = mock(Consumer.class);
        when(pulsarClient.newConsumer(Schema.BYTES)).thenReturn(consumerBuilder);
        when(consumerBuilder.topic(anyString())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionName(anyString())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscriptionType(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.negativeAckRedeliveryDelay(anyLong(), any())).thenReturn(consumerBuilder);
        when(consumerBuilder.messageListener(any())).thenReturn(consumerBuilder);
        when(consumerBuilder.subscribe()).thenReturn(consumer);
        when(consumer.isConnected()).thenReturn(true);
        AtomicReference<MQEvent> received = new AtomicReference<>();
        AtomicReference<Acknowledgment> acknowledgment = new AtomicReference<>();
        PulsarMQClient client = new PulsarMQClient(pulsarClient, new PulsarProperties()) {
            @Override
            public void consume(MQListener listener, MQEvent event, Acknowledgment ack) {
                received.set(event);
                acknowledgment.set(ack);
            }
        };
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("pulsar");
        MQListener listener = MQListener.builder().group("test").topic("orders").tags("*").build();
        client.init(Collections.singletonList(listener), properties, new MQEventSerialization() {
            @Override
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return type.cast(new MQEvent());
            }
        }, null);
        org.mockito.ArgumentCaptor<MessageListener> captor = org.mockito.ArgumentCaptor.forClass(MessageListener.class);
        verify(consumerBuilder).messageListener(captor.capture());
        Message<byte[]> message = mock(Message.class);
        when(message.getValue()).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(message.getProperty(MQDeliveryHeaders.MESSAGE_ID)).thenReturn("stable-id");
        when(message.getProperty(MQDeliveryHeaders.CORRELATION_ID)).thenReturn("correlation-id");
        when(message.getProperty(MQDeliveryHeaders.CAUSATION_ID)).thenReturn("causation-id");

        captor.getValue().received(consumer, message);

        assertEquals("correlation-id", received.get().getCorrelationId());
        assertEquals("causation-id", received.get().getCausationId());
        assertEquals("correlation-id", acknowledgment.get().correlationId());
        verify(consumer).acknowledge(message);

        Message<byte[]> correlationOnly = mock(Message.class);
        when(correlationOnly.getValue()).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(correlationOnly.getProperty(MQDeliveryHeaders.CORRELATION_ID)).thenReturn("single-correlation");
        captor.getValue().received(consumer, correlationOnly);
        assertEquals("single-correlation", received.get().getCorrelationId());
        assertNull(received.get().getCausationId());
        assertEquals("single-correlation", acknowledgment.get().correlationId());

        Message<byte[]> withoutCausality = mock(Message.class);
        when(withoutCausality.getValue()).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        captor.getValue().received(consumer, withoutCausality);
        assertNull(received.get().getCorrelationId());
        assertNull(received.get().getCausationId());
        assertNull(acknowledgment.get().correlationId());
        verify(consumer).acknowledge(correlationOnly);
        verify(consumer).acknowledge(withoutCausality);
        client.close();
        verify(pulsarClient, never()).close();
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

        client.close();
        client.close();
        verify(producer).flush();
        verify(producer).close();
        verify(pulsarClient, org.mockito.Mockito.never()).close();
    }
}
