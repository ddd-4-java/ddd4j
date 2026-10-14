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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MQOutboxRecordTest {

    @Test
    void pending_shouldUsePendingStateAndEnforceStableMessageIdHeader() {
        MQOutboxRecord record = MQOutboxRecord.pending("message-1", "orders.created", "{}",
                Map.of(MQDeliveryHeaders.MESSAGE_ID, "incorrect", "tenant", "tenant-1"), Instant.EPOCH);

        assertEquals(MQOutboxStatus.PENDING, record.status());
        assertEquals(0, record.attempts());
        assertEquals("message-1", record.headers().get(MQDeliveryHeaders.MESSAGE_ID));
        assertEquals("tenant-1", record.headers().get("tenant"));
    }

    @Test
    void constructor_shouldRejectBlankIdentityAndDestination() {
        assertThrows(IllegalArgumentException.class, () -> MQOutboxRecord.pending("", "orders.created", "{}",
                Map.of(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> MQOutboxRecord.pending("message-1", " ", "{}",
                Map.of(), Instant.EPOCH));
    }

    @Test
    void headers_shouldKeepInputOrderAndRejectNullEntries() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("zeta", "z");
        headers.put("alpha", "a");
        MQOutboxRecord record = MQOutboxRecord.pending("message-1", "orders.created", "{}",
                headers, Instant.EPOCH);

        headers.put("later", "value");
        assertEquals(Arrays.asList("zeta", "alpha", MQDeliveryHeaders.MESSAGE_ID),
                new ArrayList<>(record.headers().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> record.headers().put("other", "value"));

        headers.put("nullable", null);
        assertThrows(NullPointerException.class, () -> MQOutboxRecord.pending(
                "message-2", "orders.created", "{}", headers, Instant.EPOCH));
        headers.remove("nullable");
        headers.put(null, "value");
        assertThrows(NullPointerException.class, () -> MQOutboxRecord.pending(
                "message-3", "orders.created", "{}", headers, Instant.EPOCH));
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
