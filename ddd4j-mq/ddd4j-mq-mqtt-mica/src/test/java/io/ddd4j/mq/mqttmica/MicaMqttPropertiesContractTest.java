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

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证三线 Mica MQTT 配置的可观察契约。 */
class MicaMqttPropertiesContractTest {

    @Test
    void qosMapsKnownValuesAndFallsBackToLevelOne() {
        MicaMqttProperties properties = new MicaMqttProperties();
        assertEquals(MqttQoS.QOS1, properties.mqttQoS());
        properties.setQos(0);
        assertEquals(MqttQoS.QOS0, properties.mqttQoS());
        properties.setQos(2);
        assertEquals(MqttQoS.QOS2, properties.mqttQoS());
        properties.setQos(3);
        assertEquals(MqttQoS.QOS1, properties.mqttQoS());
    }

    @Test
    void clientIdUsesConfiguredPrefixAndRemainsUnique() {
        MicaMqttProperties properties = new MicaMqttProperties();
        properties.setClientIdPrefix("orders-");
        String first = properties.newClientId();
        String second = properties.newClientId();
        assertTrue(first.startsWith("orders-"));
        assertTrue(second.startsWith("orders-"));
        assertNotEquals(first, second);
        properties.setClientIdPrefix(null);
        assertTrue(properties.newClientId().startsWith("ddd4j-mica-"));
    }
}
