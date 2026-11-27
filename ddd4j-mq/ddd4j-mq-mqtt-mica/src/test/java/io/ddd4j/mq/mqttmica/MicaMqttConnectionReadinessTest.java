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

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订阅必须等到 MQTT CONNACK 完成，避免 mica 将订阅留在仅供重连处理的队列中。
 */
class MicaMqttConnectionReadinessTest {

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>MicaMqttMQClient.awaitConnected()</code>\u3001<code>readinessChecks.incrementAndGet()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void waitsForNativeClientConnectionBeforeSubscription() throws Exception {
        AtomicInteger readinessChecks = new AtomicInteger();

        MicaMqttMQClient.awaitConnected(() -> readinessChecks.incrementAndGet() >= 3);

        assertThat(readinessChecks).hasValue(3);
    }
}
