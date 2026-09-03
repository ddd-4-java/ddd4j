package io.ddd4j.mq.mqttmica;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MicaMqttAcknowledgmentContractTest {

    @Test
    void shouldMarkNackAsHandledBecauseMicaHasNoNativeRedelivery() {
        MicaMqttAcknowledgment acknowledgment = new MicaMqttAcknowledgment(11L, "orders/paid", null);

        acknowledgment.nack(true);

        assertTrue(acknowledgment.isAcknowledged());
    }
}
