package io.ddd4j.mq.disruptor;

import com.lmax.disruptor.RingBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisruptorAdapterContractTest {

    @Test
    void shouldRetainStableMessageIdWhenNackRequeuesLocally() {
        DisruptorEvent event = new DisruptorEvent();
        event.setMessageId("stable-id");
        event.setTopic("orders");
        RingBuffer<DisruptorEvent> ringBuffer = RingBuffer.createSingleProducer(DisruptorEvent::new, 8);
        DisruptorAcknowledgment acknowledgment = new DisruptorAcknowledgment(event, ringBuffer, 3L);

        acknowledgment.nack(true);

        assertTrue(acknowledgment.isAcknowledged());
        assertEquals("stable-id", ringBuffer.get(ringBuffer.getCursor()).getMessageId());
    }
}
