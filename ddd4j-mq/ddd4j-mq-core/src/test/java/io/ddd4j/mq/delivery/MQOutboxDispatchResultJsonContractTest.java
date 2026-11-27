package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>JsonMapper.builder()</code>\u3001<code>mapper.writeValueAsString()</code>\u3001<code>mapper.readValue()</code> \u9a8c\u8bc1 <code>MQOutboxDispatchResultJsonContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class MQOutboxDispatchResultJsonContractTest {

    /**
     * \u9a8c\u8bc1<code>mapper.readTree(json).size()</code> \u7b49\u4e8e <code>5</code>\uff0c<code>restored</code> \u7b49\u4e8e <code>result</code>\uff0c<code>restored.toString()</code> \u7b49\u4e8e <code>"MQOutboxDispatchResult[claimed=5, published=1, rescheduled=2, dead=1, confirmationLost=1]"</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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
