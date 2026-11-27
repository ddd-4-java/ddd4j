package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>Instant.parse()</code>\u3001<code>Collections.singletonMap()</code>\u3001<code>JsonMapper.builder()</code> \u9a8c\u8bc1 <code>MQOutboxRecordJsonContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class MQOutboxRecordJsonContractTest {

    /**
     * \u9a8c\u8bc1<code>mapper.readTree(json).size()</code> \u7b49\u4e8e <code>11</code>\uff0c<code>restored</code> \u7b49\u4e8e <code>record</code>\uff0c<code>restored.headers().get(MQDeliveryHeaders.CORRELATION_ID)</code> \u7b49\u4e8e <code>"correlation-1"</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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
