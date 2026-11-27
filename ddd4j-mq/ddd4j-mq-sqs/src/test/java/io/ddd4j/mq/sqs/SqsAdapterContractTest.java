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

/** \u901a\u8fc7 <code>mock()</code>\u3001<code>BaseContext.inject()</code>\u3001<code>event.setTopic()</code> \u9a8c\u8bc1 <code>SqsAdapterContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class SqsAdapterContractTest {

    /** \u9a8c\u8bc1<code>SqsMQClient.messageId(message)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>acknowledgment.messageId()</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
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
