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
package io.ddd4j.mq.message;

import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** \u9a8c\u8bc1 <code>MessageHeaders</code> \u5728<code>messageIdHeader</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class MessageHeadersTest {

    /** \u9a8c\u8bc1<code>MessageHeaders.HEADER_MESSAGE_ID</code> \u7b49\u4e8e <code>MQDeliveryHeaders.MESSAGE_ID</code>\uff0c<code>MessageHeaders.HEADER_MESSAGE_ID</code> \u4e0d\u7b49\u4e8e <code>MessageHeaders.LEGACY_HEADER_MESSAGE_ID</code>\u3002 */
    @Test
    void messageIdHeader_shouldUseTheStableDeliveryContract() {
        assertEquals(MQDeliveryHeaders.MESSAGE_ID, MessageHeaders.HEADER_MESSAGE_ID);
        assertNotEquals(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, MessageHeaders.HEADER_MESSAGE_ID);
    }
}
