package io.ddd4j.mq.mqttmica;

import io.ddd4j.mq.message.MessageHeaders;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicaMqttMessageHeaderTest {

    @Test
    void shouldPreferStableUserPropertyAndReadLegacyProperty() {
        MqttPublishMessage stableMessage = MqttPublishMessage.builder()
                .topicName("orders")
                .qos(MqttQoS.QOS1)
                .payload(new byte[0])
                .properties(properties -> {
                    properties.addUserProperty(MessageHeaders.HEADER_MESSAGE_ID, "stable-id");
                    properties.addUserProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
                })
                .build();
        assertEquals("stable-id", MicaMqttMQClient.messageId(stableMessage));

        MqttPublishMessage legacyMessage = MqttPublishMessage.builder()
                .topicName("orders")
                .qos(MqttQoS.QOS1)
                .payload(new byte[0])
                .properties(properties -> properties.addUserProperty(
                        MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id"))
                .build();
        assertEquals("legacy-id", MicaMqttMQClient.messageId(legacyMessage));
    }
}
