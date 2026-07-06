package io.ddd4j.mq.activemq.ack;

import io.ddd4j.mq.consume.UnsupportedAckOperationException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ActiveMQAcknowledgmentTest {

    @Test
    void ackShouldAcknowledgeJmsMessageOnce() throws Exception {
        Session session = mock(Session.class);
        Message message = mock(Message.class);
        ActiveMQAcknowledgment ack = new ActiveMQAcknowledgment(session, message, 7L, "msg-1", "corr-1");

        ack.ack();

        verify(message).acknowledge();
        assertTrue(ack.isAcknowledged());
        assertThrows(UnsupportedAckOperationException.class, ack::ack);
    }

    @Test
    void nackWithRequeueShouldRecoverSession() throws Exception {
        Session session = mock(Session.class);
        Message message = mock(Message.class);
        ActiveMQAcknowledgment ack = new ActiveMQAcknowledgment(session, message, 8L, "msg-2", "corr-2");

        ack.nack(true);

        verify(session).recover();
        assertTrue(ack.isAcknowledged());
    }
}
