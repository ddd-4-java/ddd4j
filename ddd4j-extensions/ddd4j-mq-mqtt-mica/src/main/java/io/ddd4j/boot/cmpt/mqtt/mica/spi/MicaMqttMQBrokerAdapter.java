package io.ddd4j.boot.cmpt.mqtt.mica.spi;

import io.ddd4j.boot.cmpt.mqtt.mica.acknowledgment.MicaMqttHeaders;
import io.ddd4j.boot.cmpt.mqtt.mica.acknowledgment.MicaMqttMessageAcknowledgment;
import io.ddd4j.boot.cmpt.mqtt.mica.acknowledgment.MicaMqttMessageAcknowledgmentFactory;
import io.ddd4j.boot.cmpt.mqtt.mica.consumer.MicaMqttMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.mqtt.mica.publisher.MicaMqttMQEventPublisher;
import io.ddd4j.boot.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.consume.MQConsumerHandler;
import io.ddd4j.boot.mq.contract.MQMessage;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.registry.MQBrokerType;
import io.ddd4j.boot.mq.registry.MQListenerDefinition;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;

/**
 * mica-mqtt Broker 适配器，桥接 ddd4j MQ SPI 与 {@link MqttClientTemplate}。
 */
@RequiredArgsConstructor
public class MicaMqttMQBrokerAdapter implements MQBrokerAdapter {

    private final MqttClientTemplate mqttClientTemplate;
    private final Ddd4jMQProperties properties;
    private final int defaultQos;
    private final MicaMqttMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.MQTT_MICA;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new MicaMqttMQEventPublisher(mqttClientTemplate, props, defaultQos);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 逻辑块：优先从 mica 原生消息解析 QoS 确认
        MqttPublishMessage micaMessage = message.nativeMessage(MqttPublishMessage.class);
        if (micaMessage != null) {
            Object topicHeader = message.headers().get(MicaMqttHeaders.TOPIC);
            String topic = topicHeader == null ? null : String.valueOf(topicHeader);
            return MicaMqttMessageAcknowledgmentFactory.from(topic, micaMessage, message.headers())
                    .acknowledgment();
        }
        MicaMqttMessageAcknowledgment micaAck = message.nativeMessage(MicaMqttMessageAcknowledgment.class);
        return micaAck;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.MQTT_MICA == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
