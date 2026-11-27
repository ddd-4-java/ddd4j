package io.ddd4j.mq.rocketmq;

import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u901a\u8fc7 <code>message.setMsgId()</code>\u3001<code>acknowledgment.nack()</code>\u3001<code>acknowledgment.isAcknowledged()</code> \u9a8c\u8bc1 <code>RocketAcknowledgmentContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class RocketAcknowledgmentContractTest {

    /** \u9a8c\u8bc1<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\uff0c<code>acknowledgment.shouldReconsume()</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldRequestBrokerReconsumeOnNack() {
        MessageExt message = new MessageExt();
        message.setMsgId("stable-id");
        RocketAcknowledgment acknowledgment = new RocketAcknowledgment(message);

        acknowledgment.nack(true);

        assertTrue(acknowledgment.isAcknowledged());
        assertTrue(acknowledgment.shouldReconsume());
    }
}
