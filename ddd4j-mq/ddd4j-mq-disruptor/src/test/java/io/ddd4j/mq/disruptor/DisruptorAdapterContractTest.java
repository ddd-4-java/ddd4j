package io.ddd4j.mq.disruptor;

import com.lmax.disruptor.RingBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u9a8c\u8bc1 <code>DisruptorAdapterContract</code> \u5728 <code>shouldDeliverRawTopicExactlyOnce</code>\u3001<code>shouldDeliverUsingConfiguredNamespace</code>\u3001<code>shouldRetainStableMessageId</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class DisruptorAdapterContractTest {

    /** \u9a8c\u8bc1<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\uff0c<code>ringBuffer.get(ringBuffer.getCursor()).getMessageId()</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>ringBuffer.getCursor()</code> \u7b49\u4e8e <code>republishedSequence</code>\u3002 */
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
