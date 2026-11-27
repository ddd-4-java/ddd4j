package io.ddd4j.mq.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u9a8c\u8bc1 <code>MqttAdapterContract</code> \u5728 <code>shouldRejectUnsupportedOperations</code>\u3001<code>shouldLeaveQosMessageUnacknowledged</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class MqttAdapterContractTest {

    /** 验证<code>acknowledgment.nack(true)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>acknowledgment.isAcknowledged()</code>为真。 */
    @Test
    void shouldTreatMessageIdAsPayloadConcernAndRejectNativeNack() {
        MqttMessage message = new MqttMessage("body".getBytes());
        MqttAcknowledgment acknowledgment = new MqttAcknowledgment(message, "orders/paid");

        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.nack(true));
        assertTrue(acknowledgment.isAcknowledged());
    }
}
