package io.ddd4j.mq.sqs.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.sqs.spi.SqsMQProperties;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AWS SQS event publisher (pure Java, AWS SDK v2).
 */
public class SqsMQEventPublisher implements MQEventPublisher {

    private final SqsClient client;
    private final SqsMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;

    public SqsMQEventPublisher(SqsClient client, SqsMQProperties properties,
                               Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
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

    private static String firstText(String... values) {
        if (Objects.isNull(values)) {
            return null;
        }
        for (String v : values) {
            if (Objects.nonNull(v) && !io.ddd4j.kit.lang.StrKit.isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        try {
            String queueUrl = firstText(destination.getTopic(), "ddd4j.default.queue");
            // 兼容 tag 形如 "queueUrl#" 表达不同目标（SQS 无 namespace 概念）
            String tag = destination.getTag();
            if (Objects.nonNull(tag) && (tag.startsWith("https://") || tag.startsWith("http://"))) {
                queueUrl = tag;
            }
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            put(attrs, MQMessages.HEADER_DESTINATION_TOPIC, destination.getTopic());
            put(attrs, MQMessages.HEADER_TENANT_ID, event.getTenantId());
            if (Objects.nonNull(event.getMsgId())) {
                put(attrs, MQMessages.HEADER_MESSAGE_ID, event.getMsgId());
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
