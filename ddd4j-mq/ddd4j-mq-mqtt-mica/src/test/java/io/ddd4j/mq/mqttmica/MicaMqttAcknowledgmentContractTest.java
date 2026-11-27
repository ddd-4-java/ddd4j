package io.ddd4j.mq.mqttmica;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u9a8c\u8bc1 <code>MicaMqttAcknowledgment</code> \u5728<code>requeueRequestsFail</code>\u3001<code>nack</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class MicaMqttAcknowledgmentContractTest {

    /** 验证<code>acknowledgment.isAcknowledged()</code>为真。 */
    @Test
    void shouldMarkNackAsHandledBecauseMicaHasNoNativeRedelivery() {
        MicaMqttAcknowledgment acknowledgment = new MicaMqttAcknowledgment(11L, "orders/paid", null);

        acknowledgment.nack(true);

        assertTrue(acknowledgment.isAcknowledged());
    }
}
