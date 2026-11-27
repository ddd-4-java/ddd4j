package io.ddd4j.mq.sqs;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;
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
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>SQS 没有 topic/tag 概念：{@code MQListener.topic} 必须直接是 queueUrl。tag 通过
 * {@link MessageHeaders#HEADER_DESTINATION_TAG} 属性传递，仅用于客户端过滤。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : SqsMQClient ###")
public class SqsMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final SqsProperties properties;
    /** \u8d1f\u8d23\u8f6e\u8be2 SQS \u961f\u5217\u7684\u6d88\u8d39\u8005\u4efb\u52a1\u3002 */
    private final List<ScheduledExecutorService> pollers = new CopyOnWriteArrayList<>();
    /** \u7528\u6765\u53d1\u9001\u548c\u63a5\u6536 SQS \u6d88\u606f\u7684\u539f\u751f\u5ba2\u6237\u7aef\u3002 */
    private SqsClient client;

    /**
     * \u6784\u9020 1\uff1a\u4f20\u5165\u914d\u7f6e\uff0c{@link #initProducer} \u65f6 lazy \u521b\u5efa SqsClient\u3002
     *
     * @param properties SQS \u8fde\u63a5\u914d\u7f6e
     */
    public SqsMQClient(SqsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * \u6784\u9020 2\uff1a\u6ce8\u5165\u5df2\u521d\u59cb\u5316\u7684\u539f\u751f {@link SqsClient}\uff08\u7528\u4e8e runtime \u96c6\u6210\u81ea\u52a8\u6ce8\u5165\uff09\u3002
     *
     * @param client \u5df2\u521d\u59cb\u5316\u7684 SQS \u5ba2\u6237\u7aef
     */
    public SqsMQClient(SqsClient client) {
        this.client = Objects.requireNonNull(client, "SqsClient");
        this.properties = new SqsProperties();
    }

    /**
     * \u5c06\u6587\u672c\u5305\u88c5\u6210 SQS \u5b57\u7b26\u4e32\u6d88\u606f\u5c5e\u6027\u3002
     * @param value \u5f53\u524d\u8981\u5199\u5165\u4ee3\u7406\u5c5e\u6027\u7684\u6570\u636e
     * @return \u53ef\u5199\u5165\u4ee3\u7406\u8bf7\u6c42\u7684\u6d88\u606f\u5c5e\u6027\u503c
     */
    private static MessageAttributeValue attrValue(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    /**
     * \u67e5\u627e SQS \u539f\u751f\u6d88\u606f\u7684\u6307\u5b9a\u5b57\u7b26\u4e32\u5c5e\u6027\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @param key \u5f85\u67e5\u8be2\u7684\u6d88\u606f\u5934\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u5339\u914d\u5230\u7684\u5b57\u7b26\u4e32\u5c5e\u6027\u503c\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    static String attr(Message message, String key) {
        if (Objects.isNull(message.messageAttributes())) {
            return null;
        }
        var v = message.messageAttributes().get(key);
        return Objects.isNull(v) ? null : v.stringValue();
    }

    /**
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(Message message) {
        String messageId = attr(message, MessageHeaders.HEADER_MESSAGE_ID);
        if (Objects.isNull(messageId)) {
            messageId = attr(message, MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
        }
        return messageId;
    }

    // ========================= 生产者 =========================

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"sqs"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "sqs";
    }

    /**
     * SQS \u65e0\u539f\u751f tag selector \u673a\u5236\uff0ctag \u8fc7\u6ee4\u53ea\u80fd\u5728\u5e94\u7528\u5c42\u7528 {@link TagMatcher#match} \u5b8c\u6210
     *  \uff08\u4e0d\u5339\u914d\u7684\u6d88\u606f\u76f4\u63a5 {@code deleteMessage} \u4e22\u5f03\uff0c\u907f\u514d\u65e0\u9650\u91cd\u6295\uff09\u3002\u6545\u8986\u5199\u8fd4\u56de false\u3002
     *
     * @return \u5f53\u524d\u4ee3\u7406\u662f\u5426\u652f\u6301\u6309\u6807\u7b7e\u8fc7\u6ee4
     */
    @Override
    public boolean supportsBrokerTagFilter() {
        return false;
    }

    // ========================= 消费者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>此实现调用 <code>properties.client()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        if (Objects.isNull(this.client)) {
            this.client = properties.client();
        }
        return event -> {
            try {
                String queueUrl = event.getTopic();
                Map<String, MessageAttributeValue> attrs = new HashMap<>();
                if (Objects.nonNull(queueUrl)) {
                    attrs.put(MessageHeaders.HEADER_DESTINATION_TOPIC, attrValue(queueUrl));
                }
                if (Objects.nonNull(event.getTenantId())) {
                    attrs.put(MessageHeaders.HEADER_TENANT_ID, attrValue(event.getTenantId()));
                }
                if (Objects.nonNull(event.getMsgId())) {
                    attrs.put(MessageHeaders.HEADER_MESSAGE_ID, attrValue(event.getMsgId()));
                }
                if (Objects.nonNull(event.getTag())) {
                    attrs.put(tagHeaderKey(), attrValue(event.getTag()));
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
        };
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>listener.getTopic()</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        String queueUrl = listener.getTopic();
        if (Objects.isNull(queueUrl) || !(queueUrl.startsWith("http://") || queueUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("SQS MQListener.topic must be a queueUrl (https://...). Got: " + queueUrl);
        }
        AtomicBoolean running = new AtomicBoolean(true);
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sqs-consumer-" + listener.getRouteExpression(this.defaultConcat()));
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
                logger().warn("SQS receive failed: binding={}", listener.getRouteExpression(this.defaultConcat()), ex);
            }
        }, 0, properties.getPollIntervalMs(), TimeUnit.MILLISECONDS);
        pollers.add(exec);
        return true;
    }

    /**
     * 执行 <code>handle Message</code>，调用 <code>TagMatcher.match</code>、<code>listener.getTags</code>、<code>client.deleteMessage</code>。
     *
     * @param message 随响应返回的业务说明
     * @param queueUrl \u5f53\u524d\u6d88\u8d39\u7684 SQS \u961f\u5217 URL
     * @param listener \u5f53\u524d\u4ee3\u7406\u8ba2\u9605\u5bf9\u5e94\u7684 DDD4J \u6d88\u606f\u76d1\u542c\u5668
     */
    private void handleMessage(Message message, String queueUrl, MQListener listener) {
        String tag = attr(message, tagHeaderKey());
        if (!TagMatcher.match(tag, listener.getTags())) {
            try {
                client.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            } catch (Exception ex) {
                logger().warn("SQS deleteMessage failed: binding={}", listener.getRouteExpression(this.defaultConcat()), ex);
            }
            return;
        }
        try {
            SqsAcknowledgment ack = new SqsAcknowledgment(client, message, queueUrl, properties.isRequeueOnNack());
            String tenantId = attr(message, MessageHeaders.HEADER_TENANT_ID);
            String payload = message.body();
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                ack.ackSingle();
                return;
            }
            if (Objects.nonNull(tenantId)) {
                event.setTenantId(tenantId);
            }
            String msgId = messageId(message);
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
                logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                try {
                    client.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .visibilityTimeout(0));
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
            try {
                client.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .visibilityTimeout(0));
            } catch (Exception ignore) {
            }
        }
    }

    /** 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。 */
    @Override
    public void start() {
        // SQS 守护线程在 initConsumer 已 scheduleWithFixedDelay
    }

    // ========================= 关闭 =========================

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
    @Override
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
