package io.ddd4j.mq.sqs.ack;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import io.ddd4j.mq.contract.MQMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * 从 SQS {@link Message} 构建 {@link SqsMessageAcknowledgment}。
 */
public final class SqsMessageAcknowledgmentFactory {

    public static final String HEADER_QUEUE_URL = "sqsQueueUrl";
    public static final String HEADER_AMAZON_SQS = "amazonSqs";

    private SqsMessageAcknowledgmentFactory() {
    }

    /**
     * 从 SQS 原生消息解析确认对象。
     *
     * @param amazonSqs SQS 客户端
     * @param queueUrl  队列 URL
     * @param message   SQS 消息
     * @return 确认对象
     */
    public static Optional<SqsMessageAcknowledgment> fromSqsMessage(
            AmazonSQS amazonSqs, String queueUrl, Message message) {
        if (amazonSqs == null || queueUrl == null || message == null) {
            return Optional.empty();
        }
        return Optional.of(new SqsMessageAcknowledgment(amazonSqs, queueUrl, message));
    }

    /**
     * 从 {@link MQMessage} 解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象
     */
    public static Optional<SqsMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Message sqsMessage = message.nativeMessage(Message.class);
        if (sqsMessage != null) {
            AmazonSQS amazonSqs = resolveAmazonSqs(message);
            String queueUrl = resolveQueueUrl(message);
            return fromSqsMessage(amazonSqs, queueUrl, sqsMessage);
        }
        SqsMessageAcknowledgment ack = message.nativeMessage(SqsMessageAcknowledgment.class);
        return ack == null ? Optional.empty() : Optional.of(ack);
    }

    /**
     * 从 MQ 头信息解析 SQS 客户端。
     */
    private static AmazonSQS resolveAmazonSqs(MQMessage<?> message) {
        Object client = message.getHeaders().get(HEADER_AMAZON_SQS);
        return client instanceof AmazonSQS amazonSqs ? amazonSqs : null;
    }

    /**
     * 从 MQ 头信息解析队列 URL。
     */
    private static String resolveQueueUrl(MQMessage<?> message) {
        Object queueUrl = message.getHeaders().get(HEADER_QUEUE_URL);
        return queueUrl == null ? null : String.valueOf(queueUrl);
    }
}
