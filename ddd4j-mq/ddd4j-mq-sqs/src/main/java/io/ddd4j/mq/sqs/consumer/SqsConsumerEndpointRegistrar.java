package io.ddd4j.mq.sqs.consumer;

import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.EndpointNaming;
import io.ddd4j.mq.listener.TagMatcher;
import io.ddd4j.mq.sqs.ack.SqsAcknowledgment;
import io.ddd4j.mq.sqs.spi.SqsMQProperties;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AWS SQS 消费者端点注册器（编程式注册）。
 *
 * <p>每个监听器启动一个轮询线程（long poll），逐条把消息转给 {@link ConsumerHandler}。
 * SQS 没有 topic/tag 概念：{@code ListenerDefinition.topic} 必须直接是 queueUrl。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SqsConsumerEndpointRegistrar {

    private final SqsClient client;
    private final SqsMQProperties properties;

    public SqsConsumerEndpointRegistrar(SqsClient client, SqsMQProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    private static String attr(Message message, String key) {
        if (Objects.isNull(message.messageAttributes())) {
            return null;
        }
        var v = message.messageAttributes().get(key);
        return Objects.isNull(v) ? null : v.stringValue();
    }

    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        String queueUrl = definition.getTopic();
        if (Objects.isNull(queueUrl) || !queueUrl.startsWith("http")) {
            throw new IllegalArgumentException("SQS Destination.topic must be a queueUrl (https://...). Got: " + queueUrl);
        }
        AtomicBoolean running = new AtomicBoolean(true);
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sqs-consumer-" + EndpointNaming.endpointId("sqs", definition));
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
                    handleMessage(client, message, queueUrl, definition, handler);
                }
            } catch (Exception ex) {
                // 业务侧自行决定：可在此记录日志 / 触发告警
            }
        }, 0, properties.getPollIntervalMs(), TimeUnit.MILLISECONDS);
        // 将 executor 引用存入 definition 以便支持后续 stop（简化：不显式 stop）
    }

    private void handleMessage(SqsClient client, Message message, String queueUrl,
                               ListenerDefinition def, ConsumerHandler handler) {
        String tag = attr(message, MessageHeaders.HEADER_DESTINATION_TAG);
        if (!TagMatcher.match(tag, def.getTags())) {
            client.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            return;
        }
        try {
            MessageConverter<Message> converter = nativeMsg -> toMessage(nativeMsg, client, queueUrl);
            Message<?> mq = converter.convert(message);
            SqsAcknowledgment ack = new SqsAcknowledgment(client, message, queueUrl, properties.isRequeueOnNack());
            handler.handle(mq, ack);
        } catch (Exception ex) {
            // 默认 nack + 立即重投
            try {
                client.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .visibilityTimeout(0));
            } catch (Exception ignore) {
            }
        }
    }

    private Message<String> toMessage(Message message, SqsClient client, String queueUrl) {
        Map<String, Object> headers = new HashMap<>();
        if (Objects.nonNull(message.messageAttributes())) {
            message.messageAttributes().forEach((k, v) -> {
                if (Objects.nonNull(v.stringValue())) {
                    headers.put(k, v.stringValue());
                }
            });
        }
        headers.put(SqsAcknowledgment.HEADER_SQS_CLIENT, client);
        headers.put(SqsAcknowledgment.HEADER_SQS_MESSAGE, message);
        headers.put(SqsAcknowledgment.HEADER_SQS_QUEUE_URL, queueUrl);
        return Message.of(message.body(), headers, message.messageId(), null, message);
    }
}
