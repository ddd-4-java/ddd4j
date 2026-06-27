package io.ddd4j.mq.disruptor.publisher;

import io.ddd4j.mq.disruptor.core.DisruptorMQBus;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.core.util.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 基于 LMAX Disruptor RingBuffer 的本地事件发布实现。
 */
@Slf4j
@RequiredArgsConstructor
public class DisruptorMQEventPublisher implements MQEventPublisher {

    private final DisruptorMQBus disruptorMQBus;
    private final Ddd4jMQProperties properties;

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");

        if (!StringUtils.hasText(event.getTopic())) {
            event.setTopic(properties.getDefaultTopic());
        }
        if (!StringUtils.hasText(event.getNamespace())) {
            event.setNamespace(properties.getNamespace());
        }
        if (event.getMsgId() == null) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }

        String namespace = StringUtils.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : event.getNamespace();
        String topic = StringUtils.hasText(destination.getTopic()) ? destination.getTopic() : event.getTopic();
        String tag = StringUtils.hasText(destination.getTag()) ? destination.getTag() : event.getTag();
        String payload = JsonKit.toJson(event);

        disruptorMQBus.publish(namespace, topic, tag, event.getMsgId(), payload);
        log.debug("Published Disruptor event: {}.{}, msgId={}", namespace, topic, event.getMsgId());
    }
}
