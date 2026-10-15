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
package io.ddd4j.web.core.idempotency;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyLeaseJsonContractTest {

    @Test
    void ownerTokenAndTtlSurviveJsonRoundTrip() {
        IdempotencyLease original = new IdempotencyLease("request-1", "owner-1", Duration.ofSeconds(30));

        String json = JsonKit.toJson(original);
        assertTrue(json.contains("\"ttl\":\"PT30S\""), json);
        IdempotencyLease restored = JsonKit.toObject(json, IdempotencyLease.class);

        assertNotNull(restored);
        assertEquals(original, restored);
        assertEquals("owner-1", restored.ownerToken());
        assertEquals(Duration.ofSeconds(30), restored.ttl());
    }
}
