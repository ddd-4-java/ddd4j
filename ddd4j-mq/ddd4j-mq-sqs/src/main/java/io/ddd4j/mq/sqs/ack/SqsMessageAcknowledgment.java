package io.ddd4j.mq.sqs.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AWS SQS manual acknowledgment mapping.
 *
 * <p>SQS 没有原生 ack 语义：ack 等价于 {@code deleteMessage(receiptHandle)}，nack(requeue=true)
 * 等价于 {@code changeMessageVisibility(receiptHandle, 0)}（让消息立即可被另一消费者接收）。
 */
public class SqsMessageAcknowledgment implements MessageAcknowledgment {

    public static final String HEADER_SQS_CLIENT = "ddd4j.sqs.client";
    public static final String HEADER_SQS_MESSAGE = "ddd4j.sqs.message";
    public static final String HEADER_SQS_QUEUE_URL = "ddd4j.sqs.queueUrl";

    private final SqsClient client;
    private final Message message;
    private final String queueUrl;
    private final String messageId;
    private final String receiptHandle;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);
    private final boolean requeueOnNack;

    public SqsMessageAcknowledgment(SqsClient client, Message message, String queueUrl, boolean requeueOnNack) {
        this.client = client;
        this.message = message;
        this.queueUrl = queueUrl;
        this.messageId = message.messageId();
        this.receiptHandle = message.receiptHandle();
        this.requeueOnNack = requeueOnNack;
    }

    @Override public long deliveryTag() { return receiptHandle == null ? 0L : (long) receiptHandle.hashCode(); }
    @Override public String messageId() { return messageId; }
    @Override public String correlationId() { return null; }
    @Override public boolean isOpen() { return client != null; }
    @Override public boolean isAcknowledged() { return acknowledged.get(); }
    @Override public MQBrokerType brokerType() { return MQBrokerType.SQS; }

    @Override public void ack() { ack(false); }

    @Override
    public void ack(boolean multiple) {
        runOnce(() -> client.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl).receiptHandle(receiptHandle).build()));
    }

    @Override public void nack(boolean requeue) { nack(false, requeue); }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (requeue && requeueOnNack) {
            runOnce(() -> client.changeMessageVisibility(b -> b
                    .queueUrl(queueUrl).receiptHandle(receiptHandle).visibilityTimeout(0)));
        } else {
            // 不重投：仍走 deleteMessage
            runOnce(() -> client.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl).receiptHandle(receiptHandle).build()));
        }
    }

    @Override public void reject(boolean requeue) { nack(false, requeue); }

    @Override public void recover(boolean requeue) { nack(false, requeue); }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (type == null) return Optional.empty();
        if (type.isInstance(client)) return Optional.of(type.cast(client));
        if (type.isInstance(message)) return Optional.of(type.cast(message));
        if (type.isInstance(this)) return Optional.of(type.cast(this));
        return Optional.empty();
    }

    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedAckOperationException("SQS message already ack'd, id=" + messageId);
        }
        try {
            op.run();
        } catch (Exception ex) {
            acknowledged.set(false);
            throw new IllegalStateException("SQS ack operation failed, id=" + messageId, ex);
        }
    }

    @FunctionalInterface
    private interface IoOperation { void run(); }
}
