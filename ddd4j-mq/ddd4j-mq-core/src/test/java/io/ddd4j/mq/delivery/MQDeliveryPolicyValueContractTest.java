package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MQDeliveryPolicyValueContractTest {

    @Test
    void valueContractPreservesDeliverySettings() {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        MQDeliveryPolicy same = new MQDeliveryPolicy(Duration.ofSeconds(60), 12,
                Duration.ofSeconds(1), Duration.ofMinutes(5), 0.20D);
        MQDeliveryPolicy different = new MQDeliveryPolicy(Duration.ofSeconds(60), 11,
                Duration.ofSeconds(1), Duration.ofMinutes(5), 0.20D);

        assertEquals(policy, same);
        assertEquals(policy.hashCode(), same.hashCode());
        assertNotEquals(policy, different);
        assertEquals("MQDeliveryPolicy[leaseDuration=PT1M, maxAttempts=12, initialBackoff=PT1S, maxBackoff=PT5M, jitterFactor=0.2]",
                policy.toString());
    }

    @Test
    void jacksonRoundTripPreservesReliableDeliveryPolicy() throws Exception {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        String json = mapper.writeValueAsString(policy);

        assertEquals(5, mapper.readTree(json).size());
        assertEquals(policy, mapper.readValue(json, MQDeliveryPolicy.class));
    }
}
