package io.ddd4j.mq.sqs.publisher;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.sqs.spi.SqsMQProperties;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AWS SQS 事件发布器（纯 Java，AWS SDK v2）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SqsMQEventPublisher implements MQEventPublisher {

    private final SqsClient client;
    private final SqsMQProperties properties;
    private final MQProperties mqProperties;
    private final MQEventSerialization serialization;

    public SqsMQEventPublisher(SqsClient client, SqsMQProperties properties,
                               MQProperties mqProperties, MQEventSerialization serialization) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    private static void put(Map<String, MessageAttributeValue> attrs, String key, String value) {
        if (Objects.isNull(value)) {
            return;
        }
        attrs.put(key, MessageAttributeValue.builder().dataType("String").stringValue(value).build());
    }

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        try {
            DestinationResolver.fillDefaults(event, mqProperties);
            String queueUrl = StrKit.hasText(destination.getTopic())
                    ? destination.getTopic()
                    : "ddd4j.default.queue";
            // 兼容 tag 形如 "queueUrl#" 表达不同目标（SQS 无 namespace 概念）
            String tag = destination.getTag();
            if (Objects.nonNull(tag) && (tag.startsWith("https://") || tag.startsWith("http://"))) {
                queueUrl = tag;
            }
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            put(attrs, MessageHeaders.HEADER_DESTINATION_TOPIC, destination.getTopic());
            put(attrs, MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
            if (Objects.nonNull(event.getMsgId())) {
                put(attrs, MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
            }
            client.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(serialization.serialize(event).toString())
                    .messageAttributes(attrs)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Publish SQS event failed", ex);
        }
    }
}
