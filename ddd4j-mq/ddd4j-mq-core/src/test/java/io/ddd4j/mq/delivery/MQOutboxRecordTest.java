package io.ddd4j.mq.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** \u9a8c\u8bc1 Outbox \u6d88\u606f\u8eab\u4efd\u3001\u6d88\u606f\u5934\u548c\u4e0d\u53ef\u4fee\u6539\u5feb\u7167\u5951\u7ea6\u3002 */
class MQOutboxRecordTest {

    /** \u9a8c\u8bc1<code>record.status()</code> \u7b49\u4e8e <code>MQOutboxStatus.PENDING</code>\uff0c<code>record.attempts()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>record.headers().get("tenant")</code> \u7b49\u4e8e <code>"tenant-1"</code>\u3002 */
    @Test
    void pending_shouldUsePendingStateAndEnforceStableMessageIdHeader() {
        MQOutboxRecord record = MQOutboxRecord.pending("message-1", "orders.created", "{}",
                Map.of(MQDeliveryHeaders.MESSAGE_ID, "incorrect", "tenant", "tenant-1"), Instant.EPOCH);

        assertEquals(MQOutboxStatus.PENDING, record.status());
        assertEquals(0, record.attempts());
        assertEquals("message-1", record.headers().get(MQDeliveryHeaders.MESSAGE_ID));
        assertEquals("tenant-1", record.headers().get("tenant"));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void constructor_shouldRejectBlankIdentityAndDestination() {
        assertThrows(IllegalArgumentException.class, () -> MQOutboxRecord.pending("", "orders.created", "{}",
                Map.of(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> MQOutboxRecord.pending("message-1", " ", "{}",
                Map.of(), Instant.EPOCH));
    }
}
