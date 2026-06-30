package io.ddd4j.mq.sqs.publisher;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;

/**
 * 基于 {@link AmazonSQS} 的领域事件发布实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class SqsMQEventPublisher implements MQEventPublisher {

    private final AmazonSQS amazonSqs;
    private final String defaultQueueUrl;
    private final Ddd4jMQProperties properties;

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");
        if (amazonSqs == null) {
            throw new IllegalStateException("AmazonSQS client is not available; configure ddd4j.mq.sqs.* properties");
        }

        // 逻辑块：补齐事件元数据
        if (!StrKit.isNotBlank(event.getTopic())) {
            event.setTopic(properties.getDefaultTopic());
        }
        if (!StrKit.isNotBlank(event.getNamespace())) {
            event.setNamespace(properties.getNamespace());
        }
        if (event.getMsgId() == null) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }

        // 逻辑块：序列化并发送到 SQS 队列
        String queueUrl = resolveQueueUrl(destination);
        String payload = JsonKit.toJson(event);
        SendMessageRequest request = new SendMessageRequest(queueUrl, payload);
        if (StrKit.isNotBlank(destination.getTag())) {
            request.withMessageGroupId(destination.getTag());
        }
        amazonSqs.sendMessage(request);
        log.debug("Published SQS event, queueUrl={}, msgId={}", queueUrl, event.getMsgId());
    }

    /**
     * 解析目标队列 URL（destination.topic 或默认配置）。
     */
    private String resolveQueueUrl(MQDestination destination) {
        if (StrKit.isNotBlank(destination.getTopic()) && destination.getTopic().startsWith("http")) {
            return destination.getTopic();
        }
        if (StrKit.isNotBlank(defaultQueueUrl)) {
            return defaultQueueUrl;
        }
        return destination.physicalDestination();
    }
}
