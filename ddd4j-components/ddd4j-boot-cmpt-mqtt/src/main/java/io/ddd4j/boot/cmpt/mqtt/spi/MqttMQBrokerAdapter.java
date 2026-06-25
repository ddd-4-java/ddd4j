package io.ddd4j.boot.cmpt.mqtt.spi;

import io.ddd4j.boot.cmpt.mqtt.acknowledgment.MqttMessageAcknowledgment;
import io.ddd4j.boot.cmpt.mqtt.acknowledgment.MqttMessageAcknowledgmentFactory;
import io.ddd4j.boot.cmpt.mqtt.consumer.MqttMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.mqtt.publisher.MqttMQEventPublisher;
import io.ddd4j.boot.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.consume.MQConsumerHandler;
import io.ddd4j.boot.mq.contract.MQMessage;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.registry.MQBrokerType;
import io.ddd4j.boot.mq.registry.MQListenerDefinition;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

/**
 * MQTT Broker 适配器，桥接 ddd4j MQ SPI 与 Spring Integration MQTT（Eclipse Paho）。
 */
@RequiredArgsConstructor
public class MqttMQBrokerAdapter implements MQBrokerAdapter {

    private final MessageChannel mqttOutboundChannel;
    private final Ddd4jMQProperties properties;
    private final int defaultQos;
    private final MqttMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.MQTT;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new MqttMQEventPublisher(mqttOutboundChannel, props, defaultQos);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 逻辑块：优先从 Spring Message 原生对象解析 MQTT QoS 确认
        Message<?> springMessage = message.nativeMessage(Message.class);
        if (springMessage != null) {
            return MqttMessageAcknowledgmentFactory.fromSpringMessage(springMessage).acknowledgment();
        }
        MqttMessageAcknowledgment mqttAck = message.nativeMessage(MqttMessageAcknowledgment.class);
        return mqttAck;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.MQTT == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
