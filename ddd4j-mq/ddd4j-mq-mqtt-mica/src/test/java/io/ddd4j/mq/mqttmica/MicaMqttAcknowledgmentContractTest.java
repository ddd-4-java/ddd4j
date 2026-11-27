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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class MicaMqttAcknowledgmentContractTest {

    /** \u9a8c\u8bc1<code>acknowledgment.nack(true)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>acknowledgment.reject(true)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u5047\u3002 */
    @Test
    void requeueRequestsFailWithoutClaimingAcknowledgment() {
        MicaMqttAcknowledgment acknowledgment = new MicaMqttAcknowledgment(11L, "orders/paid", null);

        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.nack(true));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.reject(true));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.recover(true));

        assertFalse(acknowledgment.isAcknowledged());
    }

    /** \u9a8c\u8bc1<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
    @Test
    void nackWithoutRequeueMarksMessageHandled() {
        MicaMqttAcknowledgment acknowledgment = new MicaMqttAcknowledgment(12L, "orders/paid", null);

        acknowledgment.nack(false);

        assertTrue(acknowledgment.isAcknowledged());
    }
}
