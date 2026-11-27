package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>JsonMapper.builder()</code>、<code>mapper.writeValueAsString()</code>、<code>mapper.readValue()</code> 验证 <code>MQOutboxDispatchResultJsonContract</code> 的运行行为。
 */
class MQOutboxDispatchResultJsonContractTest {

    /**
     * 验证<code>mapper.readTree(json).size()</code> 等于 <code>5</code>，<code>restored</code> 等于 <code>result</code>，<code>restored.toString()</code> 等于 <code>"MQOutboxDispatchResult[claimed=5, published=1, rescheduled=2, dead=1, confirmationLost=1]"</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void dispatchOutcomeRoundTripKeepsAllFiveCounters() throws Exception {
        MQOutboxDispatchResult result = new MQOutboxDispatchResult(5, 1, 2, 1, 1);
        JsonMapper mapper = JsonMapper.builder().build();

        String json = mapper.writeValueAsString(result);
        MQOutboxDispatchResult restored = mapper.readValue(json, MQOutboxDispatchResult.class);

        assertEquals(5, mapper.readTree(json).size());
        assertEquals(result, restored);
        assertEquals(5, restored.getClaimed());
        assertEquals(1, restored.getPublished());
        assertEquals(2, restored.getRescheduled());
        assertEquals(1, restored.getDead());
        assertEquals(1, restored.getConfirmationLost());
        assertEquals(1, restored.confirmationLost());
        assertEquals("MQOutboxDispatchResult[claimed=5, published=1, rescheduled=2, dead=1, confirmationLost=1]", restored.toString());
    }
}
