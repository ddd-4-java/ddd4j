package io.ddd4j.mq.tdmq;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdmqAcknowledgmentContractTest {

    @Test
    void shouldForwardRequeueIntentToProtocolAdapter() {
        AtomicReference<Boolean> requeue = new AtomicReference<>();
        TdmqAcknowledgment acknowledgment = new TdmqAcknowledgment("stable-id", null, 6L, requeue::set);

        acknowledgment.nack(true);

        assertEquals(Boolean.TRUE, requeue.get());
        assertTrue(acknowledgment.isAcknowledged());
    }
}
