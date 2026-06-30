package io.ddd4j.mq.mqttmica;

import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.mqttmica.ack.MicaMqttMessageAcknowledgment;
import io.ddd4j.mq.mqttmica.spi.MicaMqttMQBrokerAdapter;
import io.ddd4j.mq.mqttmica.spi.MicaMqttProperties;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicaMqttMQBrokerAdapterTest {

    @Test
    void propertiesShouldMapIntegerQosToMicaEnum() {
        MicaMqttProperties properties = new MicaMqttProperties();

        properties.setQos(0);
        assertEquals(MqttQoS.QOS0, properties.mqttQoS());
        properties.setQos(1);
        assertEquals(MqttQoS.QOS1, properties.mqttQoS());
        properties.setQos(2);
        assertEquals(MqttQoS.QOS2, properties.mqttQoS());
        properties.setQos(99);
        assertEquals(MqttQoS.QOS1, properties.mqttQoS());
    }

    @Test
    void ackShouldBeSingleUseMarker() {
        MicaMqttMessageAcknowledgment ack = new MicaMqttMessageAcknowledgment(11L, "sales/order", "corr-1");

        ack.ack();

        assertTrue(ack.isAcknowledged());
        assertEquals("11", ack.messageId());
        assertThrows(UnsupportedAckOperationException.class, ack::ack);
    }

    @Test
    void supportsMicaMqttBrokerType() {
        MicaMqttMQBrokerAdapter adapter = MicaMqttMQBrokerAdapter.disconnected(
                new MicaMqttProperties(), new Ddd4jMQProperties(), new JsonMQMessageSerialization());

        assertTrue(adapter.supports(MQBrokerType.MQTT_MICA));
        assertEquals(MQBrokerType.MQTT_MICA, adapter.brokerType());
    }
}
