package io.ddd4j.mq.nats.publisher;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQDestinationResolver;
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
 * 基于 {@link Connection} / JetStream 的领域事件发布实现。
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

        MQDestinationResolver.fillDefaults(event, properties);
        String subject = MQDestinationResolver.resolvePhysicalAddress(event, destination, properties);
        String payload = JsonKit.toJson(event);
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);

        try {
            JetStream jetStream = connection.jetStream();
            jetStream.publish(subject, body);
            log.debug("Published NATS JetStream event, subject={}, msgId={}", subject, event.getMsgId());
        } catch (IOException | JetStreamApiException ex) {
            connection.publish(subject, body);
            log.debug("Published NATS core event, subject={}, msgId={}", subject, event.getMsgId());
        }
    }
}
