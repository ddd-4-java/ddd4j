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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;

import io.ddd4j.mq.message.MessageHeaders;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.properties.UserProperties;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * MicaMqtt 用户属性读取测试。
 *
 * <p>mica-mqtt 2.6.6 的编码器要求 USER_PROPERTY 使用 {@code UserProperties} 集合，
 * 不能使用便捷方法创建的单个 UserProperty。本测试覆盖稳定键优先与旧键回退。
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
                    UserProperties userProperties = new UserProperties();
                    userProperties.add(MessageHeaders.HEADER_MESSAGE_ID, "stable-id");
                    userProperties.add(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
                    properties.getProperties().add(userProperties);
                })
                .build();
        assertEquals("stable-id", MicaMqttMQClient.messageId(stableMessage));

        MqttPublishMessage legacyMessage = MqttPublishMessage.builder()
                .topicName("orders")
                .qos(MqttQoS.QOS1)
                .payload(new byte[0])
                .properties(properties -> {
                    UserProperties userProperties = new UserProperties();
                    userProperties.add(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
                    properties.getProperties().add(userProperties);
                })
                .build();
        assertEquals("legacy-id", MicaMqttMQClient.messageId(legacyMessage));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>adapter.close()</code>\u3001<code>never()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldNotStopInjectedClient() {
        MqttClient client = mock(MqttClient.class);
        MicaMqttMQClient adapter = new MicaMqttMQClient(client);
        adapter.close();
        adapter.close();
        verify(client, never()).disconnect();
        verify(client, never()).stop();
    }
}
