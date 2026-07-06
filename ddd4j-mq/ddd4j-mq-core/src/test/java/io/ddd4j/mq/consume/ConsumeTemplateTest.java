package io.ddd4j.mq.consume;

import io.ddd4j.mq.consume.ack.AckType;
import io.ddd4j.mq.message.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsumeTemplate} 消费模板与 Ack 状态机单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ConsumeTemplateTest {

    @Test
    void preCheckDiscardShouldAckSingle() {
        ConsumeTemplate.RecordingAcknowledgment ack = new ConsumeTemplate.RecordingAcknowledgment();
        Message<String> message = Message.of("payload", "msg-1");

        ConsumeTemplate.execute(message, ack, () -> ConsumeTemplate.PRE_DISCARD, () -> AckType.ACK);

        assertTrue(ack.isAcknowledged());
        assertEquals("ackSingle", ack.lastOperation());
    }

    @Test
    void preCheckDeferShouldRequeue() {
        ConsumeTemplate.RecordingAcknowledgment ack = new ConsumeTemplate.RecordingAcknowledgment();

        ConsumeTemplate.execute(
                Message.of("payload", "msg-2"),
                ack,
                () -> ConsumeTemplate.PRE_DEFER,
                () -> AckType.ACK);

        assertEquals("nackRequeue", ack.lastOperation());
    }

    @Test
    void businessAckShouldAckSingle() {
        ConsumeTemplate.RecordingAcknowledgment ack = new ConsumeTemplate.RecordingAcknowledgment();

        ConsumeTemplate.execute(
                Message.of("payload", "msg-3"),
                ack,
                () -> ConsumeTemplate.PRE_CONTINUE,
                () -> AckType.ACK);

        assertEquals("ackSingle", ack.lastOperation());
    }

    @Test
    void businessDiscardShouldAckSingle() {
        ConsumeTemplate.RecordingAcknowledgment ack = new ConsumeTemplate.RecordingAcknowledgment();

        ConsumeTemplate.execute(
                Message.of("payload", "msg-4"),
                ack,
                () -> ConsumeTemplate.PRE_CONTINUE,
                () -> AckType.DISCARD);

        assertEquals("ackSingle", ack.lastOperation());
    }

    @Test
    void businessRequeueShouldNackRequeue() {
        ConsumeTemplate.RecordingAcknowledgment ack = new ConsumeTemplate.RecordingAcknowledgment();

        ConsumeTemplate.execute(
                Message.of("payload", "msg-5"),
                ack,
                () -> ConsumeTemplate.PRE_CONTINUE,
                () -> AckType.REQUEUE);

        assertEquals("nackRequeue", ack.lastOperation());
    }

    @Test
    void businessRejectToDlqShouldDiscard() {
        ConsumeTemplate.RecordingAcknowledgment ack = new ConsumeTemplate.RecordingAcknowledgment();

        ConsumeTemplate.execute(
                Message.of("payload", "msg-6"),
                ack,
                () -> ConsumeTemplate.PRE_CONTINUE,
                () -> AckType.REJECT_TO_DLQ);

        assertEquals("nackDiscard", ack.lastOperation());
    }

    @Test
    void businessDeferShouldRequeue() {
        ConsumeTemplate.RecordingAcknowledgment ack = new ConsumeTemplate.RecordingAcknowledgment();

        ConsumeTemplate.execute(
                Message.of("payload", "msg-7"),
                ack,
                () -> ConsumeTemplate.PRE_CONTINUE,
                () -> AckType.DEFER);

        assertEquals("nackRequeue", ack.lastOperation());
    }

    @Test
    void nullDispositionShouldRequeue() {
        ConsumeTemplate.RecordingAcknowledgment ack = new ConsumeTemplate.RecordingAcknowledgment();

        ConsumeTemplate.applyDisposition(ack, null);

        assertEquals("nackRequeue", ack.lastOperation());
    }
}
