package io.ddd4j.mq.kafka;

import io.ddd4j.mq.message.MessageHeaders;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaAcknowledgmentContractTest {

    @Test
    void shouldReadStableMessageIdAndCommitOffsetOnAck() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 8L, "key", "body");
        record.headers().add(MessageHeaders.HEADER_MESSAGE_ID, "stable-id".getBytes(StandardCharsets.UTF_8));
        Consumer<?, ?> consumer = mock(Consumer.class);
        KafkaMessageAcknowledgment acknowledgment = new KafkaMessageAcknowledgment(consumer, record);

        assertEquals("stable-id", acknowledgment.messageId());
        acknowledgment.ack();

        verify(consumer).commitSync(anyMap());
        assertTrue(acknowledgment.isAcknowledged());
    }
}
