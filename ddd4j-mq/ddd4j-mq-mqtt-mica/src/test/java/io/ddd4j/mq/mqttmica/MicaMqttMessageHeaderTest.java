package io.ddd4j.mq.mqttmica;

import io.ddd4j.mq.message.MessageHeaders;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MicaMqtt 用户属性读取测试。
 *
 * <p>mica-mqtt 2.6.9 已知 bug：{@code MqttPublishProperties.getUserPropertiesMap()}
 * 始终返回空 Map（{@code listAll()} 返回 {@code UserProperties} 集合对象，
 * 但 {@code getUserProperties()} 按 {@code instanceof UserProperty} 过滤，导致漏掉全部用户属性）。
 * 待 mica-mqtt 上游修复后取消 @Disabled。
 */
@Disabled("mica-mqtt 2.6.9 bug: getUserPropertiesMap() always returns empty map")
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
