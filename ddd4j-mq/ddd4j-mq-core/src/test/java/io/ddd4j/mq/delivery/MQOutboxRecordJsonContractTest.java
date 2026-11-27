package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>Instant.parse()</code>、<code>Collections.singletonMap()</code>、<code>JsonMapper.builder()</code> 验证 <code>MQOutboxRecordJsonContract</code> 的运行行为。
 */
class MQOutboxRecordJsonContractTest {

    /**
     * 验证<code>mapper.readTree(json).size()</code> 等于 <code>11</code>，<code>restored</code> 等于 <code>record</code>，<code>restored.headers().get(MQDeliveryHeaders.CORRELATION_ID)</code> 等于 <code>"correlation-1"</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
