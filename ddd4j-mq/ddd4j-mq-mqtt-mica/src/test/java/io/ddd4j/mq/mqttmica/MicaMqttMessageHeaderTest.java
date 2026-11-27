package io.ddd4j.mq.mqttmica;

import io.ddd4j.mq.message.MessageHeaders;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MicaMqtt \u7528\u6237\u5c5e\u6027\u8bfb\u53d6\u6d4b\u8bd5\u3002
 *
 *  <p>mica-mqtt 2.6.6 \u7684\u7f16\u7801\u5668\u8981\u6c42 USER_PROPERTY \u4f7f\u7528 {@code UserProperties} \u96c6\u5408\uff0c
 *  \u4e0d\u80fd\u4f7f\u7528\u4fbf\u6377\u65b9\u6cd5\u521b\u5efa\u7684\u5355\u4e2a UserProperty\u3002\u672c\u6d4b\u8bd5\u8986\u76d6\u7a33\u5b9a\u952e\u4f18\u5148\u4e0e\u65e7\u952e\u56de\u9000\u3002
 */
class MicaMqttMessageHeaderTest {

    /** \u9a8c\u8bc1<code>MicaMqttMQClient.messageId(stableMessage)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>MicaMqttMQClient.messageId(legacyMessage)</code> \u7b49\u4e8e <code>"legacy-id"</code>\u3002 */
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
