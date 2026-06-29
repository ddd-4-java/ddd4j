package io.ddd4j.mq.mqtt.mica.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 mica-mqtt {@link MqttClientTemplate} 的领域事件发布实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MicaMqttMQEventPublisher implements MQEventPublisher {

    private final MqttClientTemplate mqttClientTemplate;
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
        MqttQoS mqttQoS = toMqttQoS(defaultQos);
        mqttClientTemplate.publish(new MqttPublishBuilder()
                .topicName(mqttTopic)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .qos(mqttQoS));

        log.debug("Published mica-mqtt event, topic={}, msgId={}, qos={}", mqttTopic, event.getMsgId(), defaultQos);
    }

    /**
     * 根据目的地与 tag 生成 MQTT 主题（namespace.topic[.tag]）。
     */
    private String buildMqttTopic(MQDestination destination, String eventTag) {
        String namespace = StringUtils.hasText(destination.getNamespace())
                ? destination.getNamespace()
                : mqProperties.getNamespace();
        String topic = StringUtils.hasText(destination.getTopic())
                ? destination.getTopic()
                : mqProperties.getDefaultTopic();
        String tag = StringUtils.hasText(destination.getTag()) ? destination.getTag() : eventTag;
        String concat = ".";
        String base = StringUtils.hasText(namespace) ? namespace + concat + topic : topic;
        if (!StringUtils.hasText(tag)) {
            return base;
        }
        return base + concat + tag;
    }

    /**
     * 将整数 QoS 映射为 mica {@link MqttQoS}。
     */
    private MqttQoS toMqttQoS(int qos) {
        return switch (qos) {
            case 2 -> MqttQoS.QOS2;
            case 1 -> MqttQoS.QOS1;
            default -> MqttQoS.QOS0;
        };
    }
}
