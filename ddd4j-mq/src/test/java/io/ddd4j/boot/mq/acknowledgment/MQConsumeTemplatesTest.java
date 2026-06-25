package io.ddd4j.boot.mq.acknowledgment;

import io.ddd4j.boot.mq.contract.MQMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MQConsumeTemplates} 消费模板与 Ack 状态机单测。
 */
class MQConsumeTemplatesTest {

    @Test
    void preCheckDiscardShouldAckSingle() {
        MQConsumeTemplates.RecordingAcknowledgment ack = new MQConsumeTemplates.RecordingAcknowledgment();
        MQMessage<String> message = MQMessage.of("payload", "msg-1");

        MQConsumeTemplates.execute(message, ack, () -> MQConsumeTemplates.PRE_DISCARD, () -> AckDisposition.ACK);

        assertTrue(ack.isAcknowledged());
        assertEquals("ackSingle", ack.lastOperation());
    }

    @Test
    void preCheckDeferShouldRequeue() {
        MQConsumeTemplates.RecordingAcknowledgment ack = new MQConsumeTemplates.RecordingAcknowledgment();

        MQConsumeTemplates.execute(
                MQMessage.of("payload", "msg-2"),
                ack,
                () -> MQConsumeTemplates.PRE_DEFER,
                () -> AckDisposition.ACK);

        assertEquals("nackRequeue", ack.lastOperation());
    }

    @Test
    void businessAckShouldAckSingle() {
        MQConsumeTemplates.RecordingAcknowledgment ack = new MQConsumeTemplates.RecordingAcknowledgment();

        MQConsumeTemplates.execute(
                MQMessage.of("payload", "msg-3"),
                ack,
                () -> MQConsumeTemplates.PRE_CONTINUE,
                () -> AckDisposition.ACK);

        assertEquals("ackSingle", ack.lastOperation());
    }

    @Test
    void businessDiscardShouldAckSingle() {
        MQConsumeTemplates.RecordingAcknowledgment ack = new MQConsumeTemplates.RecordingAcknowledgment();

        MQConsumeTemplates.execute(
                MQMessage.of("payload", "msg-4"),
                ack,
                () -> MQConsumeTemplates.PRE_CONTINUE,
                () -> AckDisposition.DISCARD);

        assertEquals("ackSingle", ack.lastOperation());
    }

    @Test
    void businessRequeueShouldNackRequeue() {
        MQConsumeTemplates.RecordingAcknowledgment ack = new MQConsumeTemplates.RecordingAcknowledgment();

        MQConsumeTemplates.execute(
                MQMessage.of("payload", "msg-5"),
                ack,
                () -> MQConsumeTemplates.PRE_CONTINUE,
                () -> AckDisposition.REQUEUE);

        assertEquals("nackRequeue", ack.lastOperation());
    }

    @Test
    void businessRejectToDlqShouldDiscard() {
        MQConsumeTemplates.RecordingAcknowledgment ack = new MQConsumeTemplates.RecordingAcknowledgment();

        MQConsumeTemplates.execute(
                MQMessage.of("payload", "msg-6"),
                ack,
                () -> MQConsumeTemplates.PRE_CONTINUE,
                () -> AckDisposition.REJECT_TO_DLQ);

        assertEquals("nackDiscard", ack.lastOperation());
    }

    @Test
    void businessDeferShouldRequeue() {
        MQConsumeTemplates.RecordingAcknowledgment ack = new MQConsumeTemplates.RecordingAcknowledgment();

        MQConsumeTemplates.execute(
                MQMessage.of("payload", "msg-7"),
                ack,
                () -> MQConsumeTemplates.PRE_CONTINUE,
                () -> AckDisposition.DEFER);

        assertEquals("nackRequeue", ack.lastOperation());
    }

    @Test
    void nullDispositionShouldRequeue() {
        MQConsumeTemplates.RecordingAcknowledgment ack = new MQConsumeTemplates.RecordingAcknowledgment();

        MQConsumeTemplates.applyDisposition(ack, null);

        assertEquals("nackRequeue", ack.lastOperation());
    }
}
