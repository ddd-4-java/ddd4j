package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MQOutboxRecordJsonContractTest {

    @Test
    void snapshotRoundTripKeepsLeaseAndMessageHeaders() throws Exception {
        Instant availableAt = Instant.parse("2026-01-02T03:04:05Z");
        Instant leaseUntil = Instant.parse("2026-01-02T03:05:05Z");
        MQOutboxRecord record = new MQOutboxRecord("message-1", "orders.created", "{}",
                Collections.singletonMap(MQDeliveryHeaders.CORRELATION_ID, "correlation-1"),
                MQOutboxStatus.LEASED, availableAt, "worker-1", leaseUntil, 2, null, null);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        String json = mapper.writeValueAsString(record);
        MQOutboxRecord restored = mapper.readValue(json, MQOutboxRecord.class);

        assertEquals(11, mapper.readTree(json).size());
        assertEquals(record, restored);
        assertEquals("message-1", restored.headers().get(MQDeliveryHeaders.MESSAGE_ID));
        assertEquals("correlation-1", restored.headers().get(MQDeliveryHeaders.CORRELATION_ID));
    }
}
