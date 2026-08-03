package io.ddd4j.mq.sqs;

import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SqsAdapterContractTest {

    @Test
    void shouldPreferStableMessageIdAndResetVisibilityForRedelivery() {
        Message message = Message.builder()
                .messageId("transport-id")
                .receiptHandle("receipt")
                .messageAttributes(Map.of(MessageHeaders.HEADER_MESSAGE_ID,
                        MessageAttributeValue.builder().dataType("String").stringValue("stable-id").build()))
                .build();
        SqsClient client = mock(SqsClient.class);
        SqsAcknowledgment acknowledgment = new SqsAcknowledgment(client, message, "http://queue", true);

        assertEquals("stable-id", SqsMQClient.messageId(message));
        acknowledgment.nack(true);

        verify(client).changeMessageVisibility(any(java.util.function.Consumer.class));
        assertTrue(acknowledgment.isAcknowledged());
    }
}
