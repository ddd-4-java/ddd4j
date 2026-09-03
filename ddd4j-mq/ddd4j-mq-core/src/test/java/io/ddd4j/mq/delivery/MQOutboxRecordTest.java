package io.ddd4j.mq.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MQOutboxRecordTest {

    @Test
    void pending_shouldUsePendingStateAndEnforceStableMessageIdHeader() {
        MQOutboxRecord record = MQOutboxRecord.pending("message-1", "orders.created", "{}",
                Map.of(MQDeliveryHeaders.MESSAGE_ID, "incorrect", "tenant", "tenant-1"), Instant.EPOCH);

        assertEquals(MQOutboxStatus.PENDING, record.status());
        assertEquals(0, record.attempts());
        assertEquals("message-1", record.headers().get(MQDeliveryHeaders.MESSAGE_ID));
        assertEquals("tenant-1", record.headers().get("tenant"));
    }

    @Test
    void constructor_shouldRejectBlankIdentityAndDestination() {
        assertThrows(IllegalArgumentException.class, () -> MQOutboxRecord.pending("", "orders.created", "{}",
                Map.of(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> MQOutboxRecord.pending("message-1", " ", "{}",
                Map.of(), Instant.EPOCH));
    }
}
