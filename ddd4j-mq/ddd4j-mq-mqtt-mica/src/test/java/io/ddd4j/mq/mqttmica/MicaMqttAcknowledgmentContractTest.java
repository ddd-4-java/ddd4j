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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>MicaMqttAcknowledgment</code> 在<code>requeueRequestsFail</code>、<code>nack</code>等操作中的行为契约。
 */
class MicaMqttAcknowledgmentContractTest {

    /**
     * 验证<code>acknowledgment.nack(true)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>acknowledgment.reject(true)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>acknowledgment.isAcknowledged()</code>为假。
     */
    @Test
    void requeueRequestsFailWithoutClaimingAcknowledgment() {
        MicaMqttAcknowledgment acknowledgment = new MicaMqttAcknowledgment(11L, "orders/paid", null);

        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.nack(true));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.reject(true));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.recover(true));

        assertFalse(acknowledgment.isAcknowledged());
    }

    /**
     * 验证<code>acknowledgment.isAcknowledged()</code>为真。
     */
    @Test
    void nackWithoutRequeueMarksMessageHandled() {
        MicaMqttAcknowledgment acknowledgment = new MicaMqttAcknowledgment(12L, "orders/paid", null);

        acknowledgment.nack(false);

        assertTrue(acknowledgment.isAcknowledged());
    }
}
