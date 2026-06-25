package io.ddd4j.boot.cmpt.nats.publisher;

import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.core.utils.JsonKit;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 {@link Connection} / JetStream 的领域事件发布实现（骨架）。
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
        if (connection == null) {
            throw new IllegalStateException("NATS Connection is not available; configure ddd4j.mq.nats.servers");
        }

        // 逻辑块：补齐事件元数据
        if (!StringUtils.hasText(event.getTopic())) {
            event.setTopic(properties.getDefaultTopic());
        }
        if (!StringUtils.hasText(event.getNamespace())) {
            event.setNamespace(properties.getNamespace());
        }
        if (event.getMsgId() == null) {
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
        String namespace = StringUtils.hasText(destination.namespace())
                ? destination.namespace()
                : properties.getNamespace();
        String topic = StringUtils.hasText(destination.topic())
                ? destination.topic()
                : properties.getDefaultTopic();
        String tag = StringUtils.hasText(destination.tag()) ? destination.tag() : eventTag;
        String base = StringUtils.hasText(namespace) ? namespace + "." + topic : topic;
        if (!StringUtils.hasText(tag)) {
            return base;
        }
        return base + "." + tag;
    }
}
