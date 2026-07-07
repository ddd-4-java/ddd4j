package io.ddd4j.mq.pulsar;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.pulsar.ack.PulsarAcknowledgment;
import io.ddd4j.mq.pulsar.publisher.PulsarMQEventPublisher;
import io.ddd4j.mq.pulsar.spi.PulsarMQProperties;
import io.ddd4j.mq.event.MQEventSerialization;
import org.apache.pulsar.client.api.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PulsarBrokerAdapterTest {

    @SuppressWarnings("unchecked")
    private static MQEventSerialization stringSerialization() {
        return new MQEventSerialization() {
            @Override
            public <S, T> T deserialize(S src, Class<T> dist) {
                return null;
            }

            @Override
            public <T> T serialize(Object src) {
                return (T) "payload";
            }
        };
    }

    @Test
    void ackShouldDelegateToPulsarConsumer() throws Exception {
        Consumer<byte[]> consumer = mock(Consumer.class);
        Message<byte[]> message = mock(Message.class);
        when(consumer.isConnected()).thenReturn(true);
        PulsarAcknowledgment ack = new PulsarAcknowledgment(consumer, message, "msg-1", "corr-1");

        ack.ack();

        verify(consumer).acknowledge(message);
        assertTrue(ack.isAcknowledged());
        assertThrows(UnsupportedOperationException.class, ack::ack);
    }

    @Test
    void cumulativeAckShouldUseMessageId() throws Exception {
        Consumer<byte[]> consumer = mock(Consumer.class);
        Message<byte[]> message = mock(Message.class);
        MessageId messageId = mock(MessageId.class);
        when(message.getMessageId()).thenReturn(messageId);
        PulsarAcknowledgment ack = new PulsarAcknowledgment(consumer, message, "msg-2", "corr-2");

        ack.ack(true);

        verify(consumer).acknowledgeCumulative(messageId);
    }

    @Test
    void publisherShouldCreateProducerForPhysicalTopicAndSendPayload() throws Exception {
        PulsarClient client = mock(PulsarClient.class);
        ProducerBuilder<byte[]> builder = mock(ProducerBuilder.class);
        Producer<byte[]> producer = mock(Producer.class);
        TypedMessageBuilder<byte[]> messageBuilder = mock(TypedMessageBuilder.class);
        when(client.newProducer(eq(Schema.BYTES))).thenReturn(builder);
        when(builder.topic("public/default/order:paid")).thenReturn(builder);
        when(builder.batchingMaxPublishDelay(10, TimeUnit.MILLISECONDS)).thenReturn(builder);
        when(builder.create()).thenReturn(producer);
        when(producer.newMessage()).thenReturn(messageBuilder);
        when(messageBuilder.value(any(byte[].class))).thenReturn(messageBuilder);
        when(messageBuilder.property(any(), any())).thenReturn(messageBuilder);
        when(messageBuilder.sendAsync()).thenReturn(CompletableFuture.completedFuture(mock(MessageId.class)));
        PulsarMQEventPublisher publisher = new PulsarMQEventPublisher(
                client, new PulsarMQProperties(), new MQProperties(), stringSerialization());
        MQEvent event = new MQEvent();
        event.setMsgId("msg-1");
        event.setTenantId("tenant-1");
        event.setTag("paid");

        publisher.publish(event, Destination.of("order", "paid"));

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(builder).topic("public/default/order:paid");
        verify(messageBuilder).value(payload.capture());
        verify(messageBuilder).property(MessageHeaders.HEADER_DESTINATION_TOPIC, "order");
        verify(messageBuilder).property(MessageHeaders.HEADER_MESSAGE_ID, "msg-1");
        verify(messageBuilder).property(MessageHeaders.HEADER_TENANT_ID, "tenant-1");
        verify(messageBuilder).property(MessageHeaders.HEADER_DESTINATION_TAG, "paid");
        verify(messageBuilder).sendAsync();
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), payload.getValue());
    }
}
