package io.ddd4j.mq.sqs.consumer;

import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import io.ddd4j.mq.sqs.ack.SqsAcknowledgment;
import io.ddd4j.mq.sqs.spi.SqsMQProperties;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AWS SQS 消费者实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQEventConsumer}，在 {@link #subscribe(MQListener, MQEventCallback)} 中启动 long-poll 轮询线程，
 * 收到消息后做 tag 过滤、提取 payload 字符串、构建 {@link SqsAcknowledgment}，
 * 通过 {@link MQEventCallback} 交给 core 统一处理。
 *
 * <p>SQS 没有 topic/tag 概念：{@code MQListener.topic} 必须直接是 queueUrl。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class SqsMQConsumer implements MQEventConsumer {

    private final SqsClient client;
    private final SqsMQProperties properties;

    /**
     * 构造 SQS 消费者。
     *
     * @param client     AWS SQS 客户端
     * @param properties SQS 配置属性
     */
    public SqsMQConsumer(SqsClient client, SqsMQProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void subscribe(MQListener listener, MQEventCallback onEvent) {
        String queueUrl = listener.getTopic();
        if (Objects.isNull(queueUrl) || !queueUrl.startsWith("http")) {
            throw new IllegalArgumentException("SQS MQListener.topic must be a queueUrl (https://...). Got: " + queueUrl);
        }
        AtomicBoolean running = new AtomicBoolean(true);
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sqs-consumer-" + listener.namespaceTopicTags());
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(() -> {
            if (!running.get()) {
                return;
            }
            try {
                List<Message> messages = client.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(properties.getMaxNumberOfMessages())
                                .waitTimeSeconds(properties.getWaitTimeSeconds())
                                .messageAttributeNames("All")
                                .build())
                        .messages();
                for (Message message : messages) {
                    handleMessage(message, queueUrl, listener, onEvent);
                }
            } catch (Exception ex) {
                // 业务侧自行决定：可在此记录日志 / 触发告警
            }
        }, 0, properties.getPollIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void handleMessage(Message message, String queueUrl, MQListener listener, MQEventCallback onEvent) {
        String tag = attr(message, MessageHeaders.HEADER_DESTINATION_TAG);
        if (!TagMatcher.match(tag, listener.getTags())) {
            client.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            return;
        }
        try {
            Acknowledgment ack = new SqsAcknowledgment(client, message, queueUrl, properties.isRequeueOnNack());
            String tenantId = attr(message, MessageHeaders.HEADER_TENANT_ID);
            onEvent.onEvent(message.body(), message.messageId(), tenantId, tag, ack);
        } catch (Throwable ex) {
            // 默认 nack + 立即重投
            try {
                client.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .visibilityTimeout(0));
            } catch (Exception ignore) {
            }
        }
    }

    private static String attr(Message message, String key) {
        if (Objects.isNull(message.messageAttributes())) {
            return null;
        }
        var v = message.messageAttributes().get(key);
        return Objects.isNull(v) ? null : v.stringValue();
    }
}
