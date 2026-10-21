package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MQOutboxDispatchResultJsonContractTest {

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
        assertEquals("MQOutboxDispatchResult[claimed=5, published=1, rescheduled=2, dead=1, confirmationLost=1]", restored.toString());
    }
}
