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

/** \u9a8c\u8bc1 <code>PulsarAdapterContract</code> \u5728 <code>doesNotRedeliver</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class PulsarAdapterContractTest {

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
}
