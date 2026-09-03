package io.ddd4j.mq.nats;

import io.nats.client.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NatsAcknowledgmentContractTest {

    @Test
    void shouldMapNackToJetStreamNakForRedelivery() {
        Message message = mock(Message.class);
        NatsAcknowledgment acknowledgment = new NatsAcknowledgment(message);

        acknowledgment.nack(true);

        verify(message).nak();
        assertTrue(acknowledgment.isAcknowledged());
    }
}
