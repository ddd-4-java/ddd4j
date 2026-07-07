package io.ddd4j.mq.sqs;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AWS SQS 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer} —— 创建 SQS 客户端，返回 {@link Consumer<MQEvent>}，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— 为每个 listener 启动 long-poll 守护线程，tag 过滤后
 *       调 {@link #consume} 统一消费，传入 {@link SqsAcknowledgment} 实现不同级别 ack</li>
 * </ul>
 *
 * <p>SQS 没有 topic/tag 概念：{@code MQListener.topic} 必须直接是 queueUrl。tag 通过
 * {@link MessageHeaders#HEADER_DESTINATION_TAG} 属性传递，仅用于客户端过滤。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class SqsClient implements MQClient {

    private final SqsProperties properties;
    private final List<ScheduledExecutorService> pollers = new CopyOnWriteArrayList<>();
    private software.amazon.awssdk.services.sqs.SqsClient client;

    public SqsClient(SqsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "sqs";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        this.client = properties.client();
        return this::publish;
    }

    private void publish(MQEvent event) {
        try {
            String queueUrl = StrKit.hasText(event.getTopic()) ? event.getTopic() : "ddd4j.default.queue";
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            put(attrs, MessageHeaders.HEADER_DESTINATION_TOPIC, event.getTopic());
            put(attrs, MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
            if (Objects.nonNull(event.getMsgId())) {
                put(attrs, MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
            }
            if (Objects.nonNull(event.getTag())) {
                put(attrs, MessageHeaders.HEADER_DESTINATION_TAG, event.getTag());
            }
            client.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(serialization().serialize(event).toString())
                    .messageAttributes(attrs)
                    .build());
            logger().info("Publish MQ [{}]: {}", queueUrl, serialization().serialize(event));
        } catch (Exception ex) {
            throw new IllegalStateException("Publish SQS event failed", ex);
        }
    }

    private static void put(Map<String, MessageAttributeValue> attrs, String key, String value) {
        if (Objects.isNull(value)) {
            return;
        }
        attrs.put(key, MessageAttributeValue.builder().dataType("String").stringValue(value).build());
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        String queueUrl = listener.getTopic();
        if (Objects.isNull(queueUrl) || !(queueUrl.startsWith("http://") || queueUrl.startsWith("https://"))) {
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
                    handleMessage(message, queueUrl, listener);
                }
            } catch (Exception ex) {
                logger().warn("SQS receive failed: binding={}", listener.namespaceTopicTags(), ex);
            }
        }, 0, properties.getPollIntervalMs(), TimeUnit.MILLISECONDS);
        pollers.add(exec);
        return true;
    }

    private void handleMessage(Message message, String queueUrl, MQListener listener) {
        String tag = attr(message, MessageHeaders.HEADER_DESTINATION_TAG);
        if (!TagMatcher.match(tag, listener.getTags())) {
            try {
                client.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            } catch (Exception ex) {
                logger().warn("SQS deleteMessage failed: binding={}", listener.namespaceTopicTags(), ex);
            }
            return;
        }
        try {
            SqsAcknowledgment ack = new SqsAcknowledgment(client, message, queueUrl, properties.isRequeueOnNack());
            String tenantId = attr(message, MessageHeaders.HEADER_TENANT_ID);
            String payload = message.body();
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                ack.ackSingle();
                return;
            }
            // 同步 tenantId/msgId
            if (Objects.nonNull(tenantId)) {
                event.setTenantId(tenantId);
            }
            String msgId = attr(message, MessageHeaders.HEADER_MESSAGE_ID);
            if (Objects.nonNull(msgId)) {
                event.setMsgId(msgId);
            }
            if (Objects.nonNull(tag)) {
                event.setTag(tag);
            }
            try {
                consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
                try {
                    client.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .visibilityTimeout(0));
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
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

    @Override
    public void start() {
        // SQS 守护线程在 initConsumer 已 scheduleWithFixedDelay
    }

    // ========================= 关闭 =========================

    public void close() {
        for (ScheduledExecutorService exec : pollers) {
            exec.shutdownNow();
        }
        pollers.clear();
        if (Objects.nonNull(client)) {
            client.close();
        }
    }
}
