package io.ddd4j.mq.mqtt;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.mqtt.ack.MqttMessageAcknowledgment;
import io.ddd4j.mq.mqtt.publisher.MqttMQEventPublisher;
import io.ddd4j.mq.mqtt.spi.MqttMQProperties;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MqttMQBrokerAdapterTest {

    @Test
    void ackShouldMarkMessageProcessedOnce() {
        MqttMessage message = new MqttMessage();
        message.setId(42);
        MqttMessageAcknowledgment ack = new MqttMessageAcknowledgment(message, "sales/order");

        ack.ack();

        assertTrue(ack.isAcknowledged());
        assertEquals("42", ack.messageId());
        assertThrows(UnsupportedAckOperationException.class, ack::ack);
    }

    @Test
    void publisherShouldPublishToNamespaceTopicTagPath() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttMQProperties properties = new MqttMQProperties();
        properties.setQos(2);
        MqttMQEventPublisher publisher = new MqttMQEventPublisher(
                client, properties, new Ddd4jMQProperties(), stringSerialization());
        MQEvent event = new MQEvent();

        publisher.publish(event, MQDestination.of("order", "paid", "sales"));

        ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(org.mockito.ArgumentMatchers.eq("sales/order/paid"), captor.capture());
        assertEquals(2, captor.getValue().getQos());
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), captor.getValue().getPayload());
    }

    @Test
    void supportsMqttBrokerType() {
        MqttMQProperties properties = new MqttMQProperties();
        io.ddd4j.mq.mqtt.spi.MqttMQBrokerAdapter adapter = new io.ddd4j.mq.mqtt.spi.MqttMQBrokerAdapter(
                mock(MqttClient.class), properties, new Ddd4jMQProperties(), stringSerialization());

        assertTrue(adapter.supports(MQBrokerType.MQTT));
        assertEquals(MQBrokerType.MQTT, adapter.brokerType());
    }

    @SuppressWarnings("unchecked")
    private static MQEventSerialization stringSerialization() {
        return new MQEventSerialization() {
            @Override
            public <S, T> T deserialize(S src, Class<T> dist) {
                return null;
            }

            @Override
            public <T> T serialize(Object src) {
                return (T) "payload";
            }
        };
    }
}
