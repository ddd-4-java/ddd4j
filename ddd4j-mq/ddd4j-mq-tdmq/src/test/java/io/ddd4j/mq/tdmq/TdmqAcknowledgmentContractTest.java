package io.ddd4j.mq.tdmq;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDMQ \u786e\u8ba4\u7ed3\u679c\u5951\u7ea6\u6d4b\u8bd5\u3002
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class TdmqAcknowledgmentContractTest {

    /** \u9a8c\u8bc1<code>requeue.get()</code> \u7b49\u4e8e <code>Boolean.TRUE</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldForwardRequeueIntentToProtocolAdapter() {
        AtomicReference<Boolean> requeue = new AtomicReference<>();
        TdmqAcknowledgment acknowledgment = new TdmqAcknowledgment("stable-id", null, 6L, requeue::set);

        acknowledgment.nack(true);

        assertEquals(Boolean.TRUE, requeue.get());
        assertTrue(acknowledgment.isAcknowledged());
    }
}
