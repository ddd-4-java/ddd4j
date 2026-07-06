package io.ddd4j.mq.mqttmica;

import io.ddd4j.mq.consume.UnsupportedAckOperationException;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.mqttmica.ack.MicaMqttAcknowledgment;
import io.ddd4j.mq.mqttmica.spi.MicaMqttBrokerAdapter;
import io.ddd4j.mq.mqttmica.spi.MicaMqttProperties;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MicaMqttBrokerAdapterTest {

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
        MicaMqttAcknowledgment ack = new MicaMqttAcknowledgment(11L, "sales/order", "corr-1");

        ack.ack();

        assertTrue(ack.isAcknowledged());
        assertEquals("11", ack.messageId());
        assertThrows(UnsupportedAckOperationException.class, ack::ack);
    }

    @Test
    void supportsMicaMqttBrokerType() {
        MicaMqttBrokerAdapter adapter = MicaMqttBrokerAdapter.disconnected(
                new MicaMqttProperties(), new MQProperties(), new JsonMQEventSerialization());

        assertTrue(adapter.supports(BrokerType.MQTT_MICA));
        assertEquals(BrokerType.MQTT_MICA, adapter.brokerType());
    }
}
