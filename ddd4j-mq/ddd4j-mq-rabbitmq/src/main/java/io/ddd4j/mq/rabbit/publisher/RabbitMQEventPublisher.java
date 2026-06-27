package io.ddd4j.mq.rabbit.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 {@link RabbitTemplate} 的领域事件发布实现。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitMQEventPublisher implements MQEventPublisher {

    private final RabbitTemplate rabbitTemplate;
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

        // 逻辑块：序列化并构建 AMQP 消息
        String payload = JsonKit.toJson(event);
        String routingKey = buildRoutingKey(destination, event.getTag());
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.getMsgId())
                .setHeader("tenantId", event.getTenantId())
                .build();

        // 逻辑块：委托 RabbitTemplate 发送（使用默认 exchange，routingKey 路由）
        rabbitTemplate.send(rabbitTemplate.getExchange(), routingKey, message);
        log.debug("Published RabbitMQ event, routingKey={}, msgId={}", routingKey, event.getMsgId());
    }

    /**
     * 根据目的地与 tag 生成路由键。
     */
    private String buildRoutingKey(MQDestination destination, String eventTag) {
        String namespace = StringUtils.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : properties.getNamespace();
        String topic = StringUtils.hasText(destination.getTopic())
                ? destination.getTopic()
                : properties.getDefaultTopic();
        String tag = StringUtils.hasText(destination.getTag()) ? destination.getTag() : eventTag;
        String base = namespace + "." + topic;
        if (!StringUtils.hasText(tag)) {
            return base;
        }
        return base + "." + tag;
    }
}
