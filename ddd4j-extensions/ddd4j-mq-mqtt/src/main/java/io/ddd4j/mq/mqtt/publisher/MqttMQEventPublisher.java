package io.ddd4j.mq.mqtt.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.core.utils.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 Spring Integration {@link org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler}
 * 的领域事件发布实现。
 */
@Slf4j
@RequiredArgsConstructor
public class MqttMQEventPublisher implements MQEventPublisher {

    private final MessageChannel mqttOutboundChannel;
    private final Ddd4jMQProperties mqProperties;
    private final int defaultQos;

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");

        // 逻辑块：补齐事件元数据
        if (!StringUtils.hasText(event.getTopic())) {
            event.setTopic(mqProperties.getDefaultTopic());
        }
        if (!StringUtils.hasText(event.getNamespace())) {
            event.setNamespace(mqProperties.getNamespace());
        }
        if (event.getMsgId() == null) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }

        // 逻辑块：序列化并发布到 MQTT 主题
        String mqttTopic = buildMqttTopic(destination, event.getTag());
        String payload = JsonKit.toJson(event);
        var message = MessageBuilder.withPayload(payload.getBytes(StandardCharsets.UTF_8))
                .setHeader(MqttHeaders.TOPIC, mqttTopic)
                .setHeader(MqttHeaders.QOS, defaultQos)
                .setHeader(MqttHeaders.ID, event.getMsgId())
                .build();

        mqttOutboundChannel.send(message);
        log.debug("Published MQTT event, topic={}, msgId={}, qos={}", mqttTopic, event.getMsgId(), defaultQos);
    }

    /**
     * 根据目的地与 tag 生成 MQTT 主题（namespace.topic[.tag]）。
     */
    private String buildMqttTopic(MQDestination destination, String eventTag) {
        String namespace = StringUtils.hasText(destination.namespace())
                ? destination.namespace()
                : mqProperties.getNamespace();
        String topic = StringUtils.hasText(destination.topic())
                ? destination.topic()
                : mqProperties.getDefaultTopic();
        String tag = StringUtils.hasText(destination.tag()) ? destination.tag() : eventTag;
        String concat = ".";
        String base = StringUtils.hasText(namespace) ? namespace + concat + topic : topic;
        if (!StringUtils.hasText(tag)) {
            return base;
        }
        return base + concat + tag;
    }
}
