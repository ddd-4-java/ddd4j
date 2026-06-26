package io.ddd4j.mq.ons.publisher;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import com.aliyun.openservices.ons.api.SendResult;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.core.utils.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 ONS {@link Producer} 的领域事件发布实现（Rocket 兼容语义）。
 */
@Slf4j
@RequiredArgsConstructor
public class OnsMQEventPublisher implements MQEventPublisher {

    private final Producer producer;
    private final Ddd4jMQProperties properties;

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");
        if (producer == null) {
            throw new IllegalStateException("ONS Producer is not available; configure ddd4j.mq.ons.* properties");
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

        // 逻辑块：序列化并发送 ONS 消息
        String topic = buildTopic(destination);
        String tag = StringUtils.hasText(destination.getTag()) ? destination.getTag() : event.getTag();
        String payload = JsonKit.toJson(event);
        Message message = new Message(topic, tag, payload.getBytes(StandardCharsets.UTF_8));
        message.setKey(event.getMsgId());
        SendResult result = producer.send(message);
        log.debug("Published ONS event, topic={}, tag={}, msgId={}, messageId={}",
                topic, tag, event.getMsgId(), result.getMessageId());
    }

    /**
     * 根据目的地生成 ONS Topic（namespace.topic）。
     */
    private String buildTopic(MQDestination destination) {
        if (StringUtils.hasText(destination.getNamespace())) {
            return destination.getNamespace() + "." + destination.getTopic();
        }
        if (StringUtils.hasText(properties.getNamespace())) {
            return properties.getNamespace() + "." + destination.physicalDestination();
        }
        return destination.physicalDestination();
    }
}
