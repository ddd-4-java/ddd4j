package io.ddd4j.mq.mqtt.mica.consumer;

import io.ddd4j.mq.mqtt.mica.acknowledgment.MicaMqttHeaders;
import io.ddd4j.mq.mqtt.mica.acknowledgment.MicaMqttMessageAcknowledgmentFactory;
import io.ddd4j.mq.mqtt.mica.config.Ddd4jMicaMqttProperties;
import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @MQEventListener} 动态注册为 mica-mqtt 编程式订阅（镜像 {@code @MqttClientSubscribe}）。
 */
@Slf4j
@RequiredArgsConstructor
public class MicaMqttMQConsumerEndpointRegistrar implements AutoCloseable {

    private final MqttClientTemplate mqttClientTemplate;
    private final Ddd4jMQProperties mqProperties;
    private final Ddd4jMicaMqttProperties micaMqttProperties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<String> subscribedTopics = new CopyOnWriteArrayList<>();

    /**
     * 注册单个监听器定义。
     *
     * @param definition 监听器定义
     * @param handler    消费处理函数
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        String mqttTopic = MQListenerEndpointNaming.physicalTopic(mqProperties, definition);
        int qos = resolveQos();
        IMqttClientMessageListener listener = (context, topic, message, payload) ->
                onMessage(topic, message, payload, definition, handler);

        // 逻辑块：按 QoS 注册 mica 订阅（等价于 @MqttClientSubscribe(qos=...)）
        switch (qos) {
            case 2:
                mqttClientTemplate.subQos2(mqttTopic, listener);
                break;
            case 1:
                mqttClientTemplate.subQos1(mqttTopic, listener);
                break;
            default:
                mqttClientTemplate.subQos0(mqttTopic, listener);
                break;
        }

        subscribedTopics.add(mqttTopic);
        registeredDefinitions.add(definition);

        log.info("Registered mica-mqtt listener: topic={}, qos={}, ackMode={}",
                mqttTopic, qos, mqProperties.getConsumer().getAckMode());
    }

    /**
     * 批量注册监听器（启动阶段调用）。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions found for mica-mqtt");
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("mica-mqtt consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    @Override
    public void close() {
        if (subscribedTopics.isEmpty()) {
            return;
        }
        try {
            mqttClientTemplate.unSubscribe(subscribedTopics.toArray(new String[0]));
        } catch (Exception ex) {
            log.warn("Failed to unsubscribe mica-mqtt topics", ex);
        }
        subscribedTopics.clear();
    }

    /**
     * 返回已登记的监听器定义（只读视图）。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 处理 mica-mqtt 消息并委托 {@link MQConsumerHandler}。
     */
    private void onMessage(
            String topic,
            MqttPublishMessage message,
            byte[] payload,
            MQListenerDefinition definition,
            MQConsumerHandler handler) {
        try {
            String payloadText = new String(payload, StandardCharsets.UTF_8);
            Map<String, Object> headers = MicaMqttMessageAcknowledgmentFactory.buildHeaders(topic, message);
            String messageId = headerAsString(headers, MicaMqttHeaders.MESSAGE_ID);

            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText,
                    headers,
                    messageId,
                    topic,
                    message);

            MicaMqttMessageAcknowledgmentFactory.MessageAcknowledgmentOrNoOp ackWrapper =
                    MicaMqttMessageAcknowledgmentFactory.from(topic, message, headers);
            MessageAcknowledgment ack = ackWrapper.acknowledgment();

            handler.handle(mqMessage, ack);

            // 逻辑块：auto 模式下对 QoS 消息自动 ack
            if (!mqProperties.getConsumer().isManualAck() && !ack.isAcknowledged() && ackWrapper.qosAck()) {
                ack.ack();
            }
        } catch (Exception ex) {
            log.error("mica-mqtt consumer failed: bean={}, method={}",
                    beanLabel(definition), definition.getMethod().getName(), ex);
        }
    }

    /**
     * 解析订阅 QoS（manual ack 对应 QoS 1，auto 对应 QoS 0）。
     */
    private int resolveQos() {
        if (mqProperties.getConsumer().isManualAck()) {
            return Math.max(micaMqttProperties.getQos(), 1);
        }
        return 0;
    }

    private String beanLabel(MQListenerDefinition definition) {
        if (definition.getBean() != null) {
            return definition.getBean().getClass().getSimpleName();
        }
        if (definition.getBeanName() != null) {
            return definition.getBeanName();
        }
        return definition.getMethod().getDeclaringClass().getSimpleName();
    }

    private String headerAsString(Map<String, Object> headers, String key) {
        Object value = headers.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
