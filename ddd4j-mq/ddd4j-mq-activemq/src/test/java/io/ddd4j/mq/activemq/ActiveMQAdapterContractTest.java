package io.ddd4j.mq.activemq;

import io.ddd4j.mq.message.MessageHeaders;
import jakarta.jms.Message;
import jakarta.jms.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveMQAdapterContractTest {

    @Test
    void shouldPreferStableMessageIdAndRequeueOnNack() throws Exception {
        Message message = mock(Message.class);
        Session session = mock(Session.class);
        when(message.getStringProperty(MessageHeaders.HEADER_MESSAGE_ID)).thenReturn("stable-id");
        when(message.getStringProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID)).thenReturn("legacy-id");

        assertEquals("stable-id", ActiveMQClient.messageId(message));

        ActiveMQAcknowledgment acknowledgment = new ActiveMQAcknowledgment(session, message, 7L, "stable-id", null);
        acknowledgment.nack(true);

        verify(session).recover();
        assertTrue(acknowledgment.isAcknowledged());
    }
}
