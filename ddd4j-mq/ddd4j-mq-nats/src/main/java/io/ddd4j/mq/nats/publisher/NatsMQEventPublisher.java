package io.ddd4j.mq.nats.publisher;

import io.ddd4j.core.domain.event.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 {@link Connection} / JetStream 的领域事件发布实现（骨架）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class NatsMQEventPublisher implements MQEventPublisher {

    private final Connection connection;
    private final Ddd4jMQProperties properties;

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");
        if (Objects.isNull(connection)) {
            throw new IllegalStateException("NATS Connection is not available; configure ddd4j.mq.nats.servers");
        }

        // 逻辑块：补齐事件元数据
        if (!StrKit.isNotBlank(event.getTopic())) {
            event.setTopic(properties.getDefaultTopic());
        }
        if (!StrKit.isNotBlank(event.getNamespace())) {
            event.setNamespace(properties.getNamespace());
        }
        if (Objects.isNull(event.getMsgId())) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }

        // 逻辑块：序列化并发布到 subject
        String subject = buildSubject(destination, event.getTag());
        String payload = JsonKit.toJson(event);
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        try {
            JetStream jetStream = connection.jetStream();
            jetStream.publish(subject, body);
            log.debug("Published NATS JetStream event, subject={}, msgId={}", subject, event.getMsgId());
        } catch (IOException | JetStreamApiException ex) {
            // 逻辑块：JetStream 不可用时回退到 Core NATS publish
            connection.publish(subject, body);
            log.debug("Published NATS core event, subject={}, msgId={}", subject, event.getMsgId());
        }
    }

    /**
     * 根据目的地与 tag 生成 NATS subject。
     */
    private String buildSubject(MQDestination destination, String eventTag) {
        String namespace = StrKit.isNotBlank(destination.getNamespace())
                ? destination.getNamespace()
                : properties.getNamespace();
        String topic = StrKit.isNotBlank(destination.getTopic())
                ? destination.getTopic()
                : properties.getDefaultTopic();
        String tag = StrKit.isNotBlank(destination.getTag()) ? destination.getTag() : eventTag;
        String base = StrKit.isNotBlank(namespace) ? namespace + "." + topic : topic;
        if (!StrKit.isNotBlank(tag)) {
            return base;
        }
        return base + "." + tag;
    }
}
