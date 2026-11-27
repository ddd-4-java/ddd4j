package io.ddd4j.mq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>BrokerType.from()</code>、<code>BrokerType.values()</code>、<code>brokerType.toConfigValue()</code> 验证 <code>BrokerTypeContract</code> 的运行行为。
 */
class BrokerTypeContractTest {

    /**
     * 验证<code>BrokerType.from("local")</code> 等于 <code>BrokerType.DISRUPTOR</code>，<code>BrokerType.from(" LOCAL_disruptor ")</code> 等于 <code>BrokerType.DISRUPTOR</code>，<code>BrokerType.from("mica_mqtt")</code> 等于 <code>BrokerType.MQTT_MICA</code>。
     */
    @Test
    void aliasesAndInputNormalizationShareTheSameBrokerContract() {
        assertEquals(BrokerType.DISRUPTOR, BrokerType.from("local"));
        assertEquals(BrokerType.DISRUPTOR, BrokerType.from(" LOCAL_disruptor "));
        assertEquals(BrokerType.REDIS_STREAM, BrokerType.from("redis"));
        assertEquals(BrokerType.REDIS_STREAM, BrokerType.from("redis-stream"));
        assertEquals(BrokerType.REDIS_STREAM, BrokerType.from(" RedisStream "));
        assertEquals(BrokerType.REDIS_STREAM, BrokerType.from("redis_stream"));
        assertEquals(BrokerType.ACTIVEMQ, BrokerType.from("activemq"));
        assertEquals(BrokerType.ACTIVEMQ, BrokerType.from(" Artemis "));
        assertEquals(BrokerType.MQTT_MICA, BrokerType.from("mica"));
        assertEquals(BrokerType.MQTT_MICA, BrokerType.from("mica_mqtt"));
    }

    /**
     * 验证<code>BrokerType.from(null)</code> 等于 <code>BrokerType.NONE</code>，<code>BrokerType.from(" \t ")</code> 等于 <code>BrokerType.NONE</code>，<code>BrokerType.from("not-a-broker")</code> 等于 <code>BrokerType.NONE</code>。
     */
    @Test
    void absentAndUnknownBrokerConfigurationSelectsNone() {
        assertEquals(BrokerType.NONE, BrokerType.from(null));
        assertEquals(BrokerType.NONE, BrokerType.from(" \t "));
        assertEquals(BrokerType.NONE, BrokerType.from(" NoNe "));
        assertEquals(BrokerType.NONE, BrokerType.from("not-a-broker"));
    }

    /**
     * 验证<code>BrokerType.from(brokerType.toConfigValue())</code> 等于 <code>brokerType</code>。
     */
    @Test
    void everySupportedBrokerCanRoundTripThroughItsConfigurationValue() {
        for (BrokerType brokerType : BrokerType.values()) {
            assertEquals(brokerType, BrokerType.from(brokerType.toConfigValue()), brokerType.name());
        }
    }
}
