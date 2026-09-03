package io.ddd4j.mq.rocketmq;

import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RocketAcknowledgmentContractTest {

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
