package io.ddd4j.mq.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttAdapterContractTest {

    @Test
    void shouldTreatMessageIdAsPayloadConcernAndRejectNativeNack() {
        MqttMessage message = new MqttMessage("body".getBytes());
        MqttAcknowledgment acknowledgment = new MqttAcknowledgment(message, "orders/paid");

        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.nack(true));
        assertTrue(acknowledgment.isAcknowledged());
    }
}
