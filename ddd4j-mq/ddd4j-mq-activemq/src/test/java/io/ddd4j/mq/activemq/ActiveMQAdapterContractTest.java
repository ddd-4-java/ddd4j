package io.ddd4j.mq.activemq;

import io.ddd4j.mq.message.MessageHeaders;
import javax.jms.Message;
import javax.jms.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
