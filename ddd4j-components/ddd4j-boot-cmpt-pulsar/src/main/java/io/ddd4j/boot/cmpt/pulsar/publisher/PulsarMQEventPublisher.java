package io.ddd4j.boot.cmpt.pulsar.publisher;

import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.core.utils.JsonKit;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 基于 {@link PulsarTemplate} 的领域事件发布实现。
 */
@Slf4j
@RequiredArgsConstructor
public class PulsarMQEventPublisher implements MQEventPublisher {

    private final PulsarTemplate<String> pulsarTemplate;
    private final Ddd4jMQProperties properties;

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");

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

        // 逻辑块：序列化并发送到 Pulsar topic
        String payload = JsonKit.toJson(event);
        String topic = buildTopic(destination, event.getTag());
        pulsarTemplate.send(topic, payload);
        log.debug("Published Pulsar event, topic={}, msgId={}", topic, event.getMsgId());
    }

    /**
     * 根据目的地与 tag 生成 Pulsar topic 名称。
     */
    private String buildTopic(MQDestination destination, String eventTag) {
        String namespace = StringUtils.hasText(destination.namespace())
                ? destination.namespace()
                : properties.getNamespace();
        String topic = StringUtils.hasText(destination.topic())
                ? destination.topic()
                : properties.getDefaultTopic();
        String tag = StringUtils.hasText(destination.tag()) ? destination.tag() : eventTag;
        String physicalTopic = StringUtils.hasText(namespace) ? namespace + "." + topic : topic;
        if (!StringUtils.hasText(tag)) {
            return physicalTopic;
        }
        return physicalTopic + "." + tag;
    }
}
