package io.ddd4j.mq.disruptor.publisher;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.disruptor.core.DisruptorMQBus;
import io.ddd4j.mq.publish.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 基于 LMAX Disruptor RingBuffer 的本地事件发布实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class DisruptorEventPublisher implements EventPublisher {

    private final DisruptorMQBus disruptorMQBus;
    private final MQProperties properties;

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");

        DestinationResolver.fillDefaults(event, properties);
        String payload = JsonKit.toJson(event);
        String namespace = Objects.nonNull(destination.getNamespace()) ? destination.getNamespace() : event.getNamespace();
        String topic = Objects.nonNull(destination.getTopic()) ? destination.getTopic() : event.getTopic();
        String tag = Objects.nonNull(destination.getTag()) ? destination.getTag() : event.getTag();

        disruptorMQBus.publish(namespace, topic, tag, event.getMsgId(), payload);
        log.debug("Published Disruptor event: {}.{}, msgId={}", namespace, topic, event.getMsgId());
    }
}
