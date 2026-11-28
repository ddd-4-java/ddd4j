package io.ddd4j.mq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>BrokerType.from()</code>\u3001<code>BrokerType.values()</code>\u3001<code>brokerType.toConfigValue()</code> \u9a8c\u8bc1 <code>BrokerTypeContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class BrokerTypeContractTest {

    /** \u9a8c\u8bc1<code>BrokerType.from("local")</code> \u7b49\u4e8e <code>BrokerType.DISRUPTOR</code>\uff0c<code>BrokerType.from(" LOCAL_disruptor ")</code> \u7b49\u4e8e <code>BrokerType.DISRUPTOR</code>\uff0c<code>BrokerType.from("mica_mqtt")</code> \u7b49\u4e8e <code>BrokerType.MQTT_MICA</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>BrokerType.from(null)</code> \u7b49\u4e8e <code>BrokerType.NONE</code>\uff0c<code>BrokerType.from(" \t ")</code> \u7b49\u4e8e <code>BrokerType.NONE</code>\uff0c<code>BrokerType.from("not-a-broker")</code> \u7b49\u4e8e <code>BrokerType.NONE</code>\u3002 */
    @Test
    void absentAndUnknownBrokerConfigurationSelectsNone() {
        assertEquals(BrokerType.NONE, BrokerType.from(null));
        assertEquals(BrokerType.NONE, BrokerType.from(" \t "));
        assertEquals(BrokerType.NONE, BrokerType.from(" NoNe "));
        assertEquals(BrokerType.NONE, BrokerType.from("not-a-broker"));
    }

    /** \u9a8c\u8bc1<code>BrokerType.from(brokerType.toConfigValue())</code> \u7b49\u4e8e <code>brokerType</code>\u3002 */
    @Test
    void everySupportedBrokerCanRoundTripThroughItsConfigurationValue() {
        for (BrokerType brokerType : BrokerType.values()) {
            assertEquals(brokerType, BrokerType.from(brokerType.toConfigValue()), brokerType.name());
        }
    }
}
