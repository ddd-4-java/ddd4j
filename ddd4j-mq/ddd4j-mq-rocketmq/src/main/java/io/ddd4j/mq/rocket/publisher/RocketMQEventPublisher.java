package io.ddd4j.mq.rocket.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 基于 {@link RocketMQTemplate} 的领域事件发布实现。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMQEventPublisher implements MQEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;
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

        // 逻辑块：序列化并构建 RocketMQ 目的地（topic:tag）
        String payload = JsonKit.toJson(event);
        String rocketDestination = buildRocketDestination(destination, event.getTag());
        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader("KEYS", event.getMsgId())
                .setHeader("tenantId", event.getTenantId())
                .build();

        // 逻辑块：委托 RocketMQTemplate 同步发送
        rocketMQTemplate.syncSend(rocketDestination, message);
        log.debug("Published RocketMQ event, destination={}, msgId={}", rocketDestination, event.getMsgId());
    }

    /**
     * 根据目的地与 tag 生成 RocketMQ destination（topic:tag）。
     */
    private String buildRocketDestination(MQDestination destination, String eventTag) {
        String namespace = StringUtils.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : properties.getNamespace();
        String topic = StringUtils.hasText(destination.getTopic())
                ? destination.getTopic()
                : properties.getDefaultTopic();
        String tag = StringUtils.hasText(destination.getTag()) ? destination.getTag() : eventTag;
        String physicalTopic = StringUtils.hasText(namespace) ? namespace + "." + topic : topic;
        if (!StringUtils.hasText(tag)) {
            return physicalTopic;
        }
        return physicalTopic + ":" + tag;
    }
}
