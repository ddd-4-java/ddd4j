package io.ddd4j.mq.sqs;

import io.ddd4j.core.domain.event.MQEvent;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.sqs.ack.SqsMessageAcknowledgment;
import io.ddd4j.mq.sqs.publisher.SqsMQEventPublisher;
import io.ddd4j.mq.sqs.spi.SqsBrokerAdapter;
import io.ddd4j.mq.sqs.spi.SqsMQProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SqsMQBrokerAdapterTest {

    @SuppressWarnings("unchecked")
    private static MQEventSerialization stringSerialization() {
        return new MQEventSerialization() {
            @Override
            public <S, T> T deserialize(S src, Class<T> dist) {
                return null;
            }

            @Override
            public <T> T serialize(Object src) {
                return (T) "payload";
            }
        };
    }

    @Test
    void ackShouldDeleteSqsMessageOnce() {
        SqsClient client = mock(SqsClient.class);
        Message message = Message.builder().messageId("msg-1").receiptHandle("receipt-1").build();
        SqsMessageAcknowledgment ack = new SqsMessageAcknowledgment(client, message, "queue-url", true);

        ack.ack();

        ArgumentCaptor<DeleteMessageRequest> captor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(client).deleteMessage(captor.capture());
        assertEquals("queue-url", captor.getValue().queueUrl());
        assertEquals("receipt-1", captor.getValue().receiptHandle());
        assertThrows(UnsupportedAckOperationException.class, ack::ack);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nackWithRequeueShouldResetVisibility() {
        SqsClient client = mock(SqsClient.class);
        Message message = Message.builder().messageId("msg-2").receiptHandle("receipt-2").build();
        SqsMessageAcknowledgment ack = new SqsMessageAcknowledgment(client, message, "queue-url", true);

        ack.nack(true);

        verify(client).changeMessageVisibility(any(Consumer.class));
        assertTrue(ack.isAcknowledged());
    }

    @Test
    void publisherShouldBuildSendMessageRequest() {
        SqsClient client = mock(SqsClient.class);
        SqsMQEventPublisher publisher = new SqsMQEventPublisher(
                client, new SqsMQProperties(), new Ddd4jMQProperties(), stringSerialization());
        MQEvent event = new MQEvent();
        event.setMsgId("msg-1");
        event.setTenantId("tenant-1");

        publisher.publish(event, MQDestination.of("fallback-queue", "https://sqs.test/queue"));

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(client).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();
        assertEquals("https://sqs.test/queue", request.queueUrl());
        assertEquals("payload", request.messageBody());
        assertEquals("fallback-queue", request.messageAttributes().get(MQMessages.HEADER_DESTINATION_TOPIC).stringValue());
        assertEquals("tenant-1", request.messageAttributes().get(MQMessages.HEADER_TENANT_ID).stringValue());
        assertEquals("msg-1", request.messageAttributes().get(MQMessages.HEADER_MESSAGE_ID).stringValue());
    }

    @Test
    void supportsSqsBrokerType() {
        SqsBrokerAdapter adapter = new SqsBrokerAdapter(
                mock(SqsClient.class), new SqsMQProperties(), new Ddd4jMQProperties(), stringSerialization());

        assertTrue(adapter.supports(MQBrokerType.SQS));
        assertEquals(MQBrokerType.SQS, adapter.brokerType());
    }
}
