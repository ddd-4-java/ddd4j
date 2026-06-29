package io.ddd4j.mq.sqs.ack;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 AWS SQS {@link Message} 的消息确认实现（DeleteMessage / ChangeMessageVisibility）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class SqsMessageAcknowledgment implements MessageAcknowledgment {

    private final AmazonSQS amazonSqs;
    private final String queueUrl;
    private final Message message;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 SQS 确认对象。
     *
     * @param amazonSqs SQS 客户端
     * @param queueUrl  队列 URL
     * @param message   SQS 消息
     */
    public SqsMessageAcknowledgment(AmazonSQS amazonSqs, String queueUrl, Message message) {
        this.amazonSqs = Objects.requireNonNull(amazonSqs, "amazonSqs");
        this.queueUrl = Objects.requireNonNull(queueUrl, "queueUrl");
        this.message = Objects.requireNonNull(message, "message");
    }

    @Override
    public long deliveryTag() {
        return message.getAttributes() == null ? 0L
                : Long.parseLong(message.getAttributes().getOrDefault("ApproximateReceiveCount", "0"));
    }

    @Override
    public String messageId() {
        return message.getMessageId();
    }

    @Override
    public String correlationId() {
        return message.getReceiptHandle();
    }

    @Override
    public boolean isOpen() {
        return !acknowledged.get();
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.SQS;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("SQS ignores multiple ack flag, deleting messageId={}", message.getMessageId());
        }
        // 逻辑块：DeleteMessage 确认消费
        amazonSqs.deleteMessage(new DeleteMessageRequest(queueUrl, message.getReceiptHandle()));
        acknowledged.set(true);
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("SQS ignores multiple nack flag, messageId={}", message.getMessageId());
        }
        if (!requeue) {
            // 逻辑块：不 requeue 时直接删除（进入 DLQ 需队列侧配置）
            amazonSqs.deleteMessage(new DeleteMessageRequest(queueUrl, message.getReceiptHandle()));
            acknowledged.set(true);
            return;
        }
        // 逻辑块：通过 visibility timeout=0 使消息立即可再次被消费
        amazonSqs.changeMessageVisibility(new ChangeMessageVisibilityRequest(
                queueUrl, message.getReceiptHandle(), 0));
        acknowledged.set(true);
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        if (requeue) {
            nack(true);
            return;
        }
        throw new UnsupportedAckOperationException("SQS recover without requeue is not supported");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (Message.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(message));
        }
        if (AmazonSQS.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(amazonSqs));
        }
        if (SqsMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回底层 SQS 消息。
     */
    public Message sqsMessage() {
        return message;
    }

    /**
     * 防止重复确认。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new IllegalStateException("SQS message already acknowledged, messageId=" + message.getMessageId());
        }
    }
}
