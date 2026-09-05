/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
