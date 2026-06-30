package io.ddd4j.mq.sqs.consumer;

import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.ddd4j.mq.registry.MQTagMatcher;
import io.ddd4j.mq.sqs.ack.SqsMessageAcknowledgment;
import io.ddd4j.mq.sqs.spi.SqsMQProperties;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Programmatic AWS SQS consumer registrar.
 *
 * <p>每个监听器启动一个轮询线程（long poll），逐条把消息转给 {@link MQConsumerHandler}。
 * SQS 没有 topic/tag 概念：{@code MQListenerDefinition.topic} 必须直接是 queueUrl。
 */
public class SqsConsumerEndpointRegistrar {

    private final SqsClient client;
    private final SqsMQProperties properties;

    public SqsConsumerEndpointRegistrar(SqsClient client, SqsMQProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        String queueUrl = definition.getTopic();
        if (queueUrl == null || !queueUrl.startsWith("http")) {
            throw new IllegalArgumentException("SQS MQDestination.topic must be a queueUrl (https://...). Got: " + queueUrl);
        }
        AtomicBoolean running = new AtomicBoolean(true);
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sqs-consumer-" + MQListenerEndpointNaming.endpointId("sqs", definition));
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(() -> {
            if (!running.get()) return;
            try {
                List<Message> messages = client.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(properties.getMaxNumberOfMessages())
                                .waitTimeSeconds(properties.getWaitTimeSeconds())
                                .messageAttributeNames("All")
                                .build())
                        .messages();
                for (Message message : messages) {
                    handleMessage(client, message, queueUrl, definition, handler);
                }
            } catch (Exception ex) {
                // 业务侧自行决定：可在此记录日志 / 触发告警
            }
        }, 0, properties.getPollIntervalMs(), TimeUnit.MILLISECONDS);
        // 将 executor 引用存入 definition 以便支持后续 stop（简化：不显式 stop）
    }

    private void handleMessage(SqsClient client, Message message, String queueUrl,
                               MQListenerDefinition def, MQConsumerHandler handler) {
        String tag = attr(message, MQMessages.HEADER_DESTINATION_TAG);
        if (!MQTagMatcher.match(tag, def.getTags())) {
            client.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            return;
        }
        try {
            MQMessage<String> mq = toMessage(message, client, queueUrl);
            SqsMessageAcknowledgment ack = new SqsMessageAcknowledgment(client, message, queueUrl, properties.isRequeueOnNack());
            handler.handle(mq, ack);
        } catch (Exception ex) {
            // 默认 nack + 立即重投
            try {
                client.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .visibilityTimeout(0));
            } catch (Exception ignore) {}
        }
    }

    private static String attr(Message message, String key) {
        if (message.messageAttributes() == null) return null;
        var v = message.messageAttributes().get(key);
        return v == null ? null : v.stringValue();
    }

    private MQMessage<String> toMessage(Message message, SqsClient client, String queueUrl) {
        Map<String, Object> headers = new HashMap<>();
        if (message.messageAttributes() != null) {
            message.messageAttributes().forEach((k, v) -> {
                if (v.stringValue() != null) headers.put(k, v.stringValue());
            });
        }
        headers.put(SqsMessageAcknowledgment.HEADER_SQS_CLIENT, client);
        headers.put(SqsMessageAcknowledgment.HEADER_SQS_MESSAGE, message);
        headers.put(SqsMessageAcknowledgment.HEADER_SQS_QUEUE_URL, queueUrl);
        return MQMessage.of(message.body(), headers, message.messageId(), null, message);
    }
}
