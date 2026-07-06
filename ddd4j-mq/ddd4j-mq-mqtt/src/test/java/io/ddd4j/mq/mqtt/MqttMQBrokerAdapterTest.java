package io.ddd4j.mq.mqtt;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.consume.UnsupportedAckOperationException;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.mqtt.ack.MqttAcknowledgment;
import io.ddd4j.mq.mqtt.publisher.MqttEventPublisher;
import io.ddd4j.mq.mqtt.spi.MqttMQProperties;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.serialization.EventSerialization;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MqttBrokerAdapterTest {

    @SuppressWarnings("unchecked")
    private static EventSerialization stringSerialization() {
        return new EventSerialization() {
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

    @Test
    void ackShouldMarkMessageProcessedOnce() {
        MqttMessage message = new MqttMessage();
        message.setId(42);
        MqttAcknowledgment ack = new MqttAcknowledgment(message, "sales/order");

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
        MqttEventPublisher publisher = new MqttEventPublisher(
                client, properties, new MQProperties(), stringSerialization());
        MQEvent event = new MQEvent();

        publisher.publish(event, Destination.of("order", "paid", "sales"));

        ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(org.mockito.ArgumentMatchers.eq("sales/order/paid"), captor.capture());
        assertEquals(2, captor.getValue().getQos());
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), captor.getValue().getPayload());
    }

    @Test
    void supportsMqttBrokerType() {
        MqttMQProperties properties = new MqttMQProperties();
        io.ddd4j.mq.mqtt.spi.MqttBrokerAdapter adapter = new io.ddd4j.mq.mqtt.spi.MqttBrokerAdapter(
                mock(MqttClient.class), properties, new MQProperties(), stringSerialization());

        assertTrue(adapter.supports(BrokerType.MQTT));
        assertEquals(BrokerType.MQTT, adapter.brokerType());
    }
}
