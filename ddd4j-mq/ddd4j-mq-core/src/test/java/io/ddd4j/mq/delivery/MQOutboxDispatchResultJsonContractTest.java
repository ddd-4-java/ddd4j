package io.ddd4j.mq.delivery;

import tools.jackson.databind.json.JsonMapper;
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
        assertEquals(1, restored.confirmationLost());
    }
}
