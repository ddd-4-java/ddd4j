package io.ddd4j.mq.mqtt.consumer;

import io.ddd4j.mq.mqtt.ack.MqttMessageAcknowledgmentFactory;
import io.ddd4j.mq.mqtt.config.Ddd4jMqttProperties;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @MQEventListener} 动态注册为 MQTT 入站适配器（Eclipse Paho）。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MqttMQConsumerEndpointRegistrar implements AutoCloseable {

    private final MqttPahoClientFactory mqttClientFactory;
    private final Ddd4jMQProperties mqProperties;
    private final Ddd4jMqttProperties mqttProperties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<MqttPahoMessageDrivenChannelAdapter> adapters = new CopyOnWriteArrayList<>();

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
        String clientId = buildClientId(definition);
        int qos = resolveQos();

        DirectChannel channel = new DirectChannel();
        channel.subscribe(message -> onMessage(message, definition, handler));

        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId, mqttClientFactory, mqttTopic);
        adapter.setCompletionTimeout(mqttProperties.getCompletionTimeout());
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qos);
        adapter.setOutputChannel(channel);
        adapter.start();

        adapters.add(adapter);
        registeredDefinitions.add(definition);

        log.info("Registered MQTT listener: clientId={}, topic={}, qos={}, ackMode={}",
                clientId, mqttTopic, qos, mqProperties.getConsumer().getAckMode());
    }

    /**
     * 批量注册监听器（启动阶段调用）。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions found for MQTT");
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("MQTT consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    @Override
    public void close() {
        for (MqttPahoMessageDrivenChannelAdapter adapter : adapters) {
            try {
                adapter.stop();
            } catch (Exception ex) {
                log.warn("Failed to stop MQTT inbound adapter", ex);
            }
        }
        adapters.clear();
    }

    /**
     * 返回已登记的监听器定义（只读视图）。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 处理 MQTT 消息并委托 {@link MQConsumerHandler}。
     */
    private void onMessage(Message<?> springMessage, MQListenerDefinition definition, MQConsumerHandler handler) {
        try {
            String payloadText = extractPayload(springMessage);
            Map<String, Object> headers = new HashMap<>(springMessage.getHeaders());
            String messageId = headerAsString(headers, MqttHeaders.ID);
            String topic = headerAsString(headers, MqttHeaders.RECEIVED_TOPIC);
            if (topic == null) {
                topic = headerAsString(headers, MqttHeaders.TOPIC);
            }

            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText,
                    headers,
                    messageId,
                    topic,
                    springMessage);

            MqttMessageAcknowledgmentFactory.MessageAcknowledgmentOrNoOp ackWrapper =
                    MqttMessageAcknowledgmentFactory.fromSpringMessage(springMessage);
            MessageAcknowledgment ack = ackWrapper.acknowledgment();

            handler.handle(mqMessage, ack);

            // 逻辑块：auto 模式下对 QoS 消息自动 ack
            if (!mqProperties.getConsumer().isManualAck() && !ack.isAcknowledged() && ackWrapper.qosAck()) {
                ack.ack();
            }
        } catch (Exception ex) {
            log.error("MQTT consumer failed: bean={}, method={}",
                    beanLabel(definition), definition.getMethod().getName(), ex);
        }
    }

    /**
     * 从 Spring Message 提取文本载荷。
     */
    private String extractPayload(Message<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (payload instanceof String text) {
            return text;
        }
        return String.valueOf(payload);
    }

    /**
     * 构建唯一客户端 ID。
     */
    private String buildClientId(MQListenerDefinition definition) {
        String endpointId = MQListenerEndpointNaming.endpointId("mqtt", definition);
        return mqttProperties.getSubscribeClientIdPrefix() + "-" + endpointId + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 解析订阅 QoS（manual ack 对应 QoS 1，auto 对应 QoS 0）。
     */
    private int resolveQos() {
        if (mqProperties.getConsumer().isManualAck()) {
            return Math.max(mqttProperties.getQos(), 1);
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
