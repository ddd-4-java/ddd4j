package io.ddd4j.mq.delivery;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** \u9a8c\u8bc1 <code>MQDeliveryPolicyValueContract</code> \u5728 <code>explicitJsonPropertiesRemainAvailable</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class MQDeliveryPolicyValueContractTest {

    /** \u9a8c\u8bc1<code>same</code> \u7b49\u4e8e <code>policy</code>\uff0c<code>same.hashCode()</code> \u7b49\u4e8e <code>policy.hashCode()</code>\uff0c<code>different</code> \u4e0d\u7b49\u4e8e <code>policy</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>mapper.readTree(json).size()</code> \u7b49\u4e8e <code>5</code>\uff0c<code>mapper.readValue(json, MQDeliveryPolicy.class)</code> \u7b49\u4e8e <code>policy</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void jacksonRoundTripPreservesReliableDeliveryPolicy() throws Exception {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        String json = mapper.writeValueAsString(policy);

        assertEquals(5, mapper.readTree(json).size());
        assertEquals(policy, mapper.readValue(json, MQDeliveryPolicy.class));
    }
}
