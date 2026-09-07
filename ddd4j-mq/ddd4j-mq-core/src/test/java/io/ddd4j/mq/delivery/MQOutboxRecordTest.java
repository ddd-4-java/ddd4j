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
package io.ddd4j.mq.delivery;

import java.util.Collections;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MQOutboxRecordTest {

    @Test
    void pending_shouldUsePendingStateAndEnforceStableMessageIdHeader() {
        MQOutboxRecord record = MQOutboxRecord.pending("message-1", "orders.created", "{}",
                new java.util.LinkedHashMap<String, String>() {{ put(MQDeliveryHeaders.MESSAGE_ID, "incorrect"); put("tenant", "tenant-1"); }}, Instant.EPOCH);

        assertEquals(MQOutboxStatus.PENDING, record.status());
        assertEquals(0, record.attempts());
        assertEquals("message-1", record.headers().get(MQDeliveryHeaders.MESSAGE_ID));
        assertEquals("tenant-1", record.headers().get("tenant"));
    }

    @Test
    void constructor_shouldRejectBlankIdentityAndDestination() {
        assertThrows(IllegalArgumentException.class, () -> MQOutboxRecord.pending("", "orders.created", "{}",
                Collections.emptyMap(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> MQOutboxRecord.pending("message-1", " ", "{}",
                Collections.emptyMap(), Instant.EPOCH));
    }

    @Test
    void toString_shouldMatchRecordValueSemanticsForEveryComponent() {
        MQOutboxRecord record = MQOutboxRecord.pending(
                "message-1", "orders.created", "{}", Collections.emptyMap(), Instant.EPOCH);

        assertEquals("MQOutboxRecord[messageId=message-1, destination=orders.created, payload={}, "
                + "headers={ddd4j-message-id=message-1}, status=PENDING, availableAt=1970-01-01T00:00:00Z, "
                + "leaseOwner=null, leaseUntil=null, attempts=0, lastError=null, publishedAt=null]",
                record.toString());
    }
}
