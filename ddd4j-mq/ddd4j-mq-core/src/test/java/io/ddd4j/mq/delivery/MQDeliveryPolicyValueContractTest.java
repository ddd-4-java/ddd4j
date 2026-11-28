package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 验证 <code>MQDeliveryPolicyValueContract</code> 在 <code>explicitJsonPropertiesRemainAvailable</code> 等场景中的行为。
 */
class MQDeliveryPolicyValueContractTest {

    /**
     * 验证<code>same</code> 等于 <code>policy</code>，<code>same.hashCode()</code> 等于 <code>policy.hashCode()</code>，<code>different</code> 不等于 <code>policy</code>。
     */
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
     * 验证<code>mapper.readTree(json).size()</code> 等于 <code>5</code>，<code>mapper.readValue(json, MQDeliveryPolicy.class)</code> 等于 <code>policy</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void jacksonRoundTripPreservesReliableDeliveryPolicy() throws Exception {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        String json = mapper.writeValueAsString(policy);

        assertEquals(5, mapper.readTree(json).size());
        assertEquals(policy, mapper.readValue(json, MQDeliveryPolicy.class));
    }

    /**
     * 验证<code>mapper.readTree(json).size()</code> 等于 <code>5</code>，<code>mapper.readValue(json, MQDeliveryPolicy.class)</code> 等于 <code>policy</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void explicitJsonPropertiesRemainAvailableWithGetterDiscoveryDisabled() throws Exception {
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .addMixIn(MQDeliveryPolicy.class, ExplicitGettersOnly.class)
                .build();

        String json = mapper.writeValueAsString(policy);

        assertEquals(5, mapper.readTree(json).size());
        assertEquals(policy, mapper.readValue(json, MQDeliveryPolicy.class));
    }

    /**
     * 只提供显式访问器的样本，用于验证实际 Bean 访问规则。
     */
    @JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
    private abstract static class ExplicitGettersOnly {
    }
}
