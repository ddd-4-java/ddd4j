package io.ddd4j.mq.pulsar;

import io.ddd4j.mq.message.MessageHeaders;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PulsarAdapterContractTest {

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
}
