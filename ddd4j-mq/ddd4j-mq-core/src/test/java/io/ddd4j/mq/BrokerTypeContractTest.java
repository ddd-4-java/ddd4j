package io.ddd4j.mq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerTypeContractTest {

    @Test
    void aliasesAndInputNormalizationShareTheSameBrokerContract() {
        assertEquals(BrokerType.DISRUPTOR, BrokerType.from(" LOCAL_disruptor "));
        assertEquals(BrokerType.REDIS_STREAM, BrokerType.from(" RedisStream "));
        assertEquals(BrokerType.REDIS_STREAM, BrokerType.from("redis_stream"));
        assertEquals(BrokerType.ACTIVEMQ, BrokerType.from(" Artemis "));
        assertEquals(BrokerType.MQTT_MICA, BrokerType.from("mica_mqtt"));
    }

    @Test
    void absentAndUnknownBrokerConfigurationSelectsNone() {
        assertEquals(BrokerType.NONE, BrokerType.from(null));
        assertEquals(BrokerType.NONE, BrokerType.from(" \t "));
        assertEquals(BrokerType.NONE, BrokerType.from(" NoNe "));
        assertEquals(BrokerType.NONE, BrokerType.from("not-a-broker"));
    }

    @Test
    void everySupportedBrokerCanRoundTripThroughItsConfigurationValue() {
        for (BrokerType brokerType : BrokerType.values()) {
            assertEquals(brokerType, BrokerType.from(brokerType.toConfigValue()), brokerType.name());
        }
    }
}
