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
package io.ddd4j.web.core;

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestFailure;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.health.ReadinessResponse;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebPayloadJsonContractTest {

    @Test
    void requestDataRetainsCapturedMetadata() {
        WebRequestData original = new WebRequestData("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");

        WebRequestData restored = JsonKit.toObject(JsonKit.toJson(original), WebRequestData.class);

        assertNotNull(restored);
        assertEquals(original, restored);
        assertEquals("203.0.113.1", restored.forwardedFor());
    }

    @Test
    void requestFailureRetainsMethodAndPathWithNullCause() {
        WebRequestFailure original = new WebRequestFailure("POST", "/orders", null);

        WebRequestFailure restored = JsonKit.toObject(JsonKit.toJson(original), WebRequestFailure.class);

        assertNotNull(restored);
        assertEquals(original, restored);
        assertEquals("/orders", restored.path());
    }

    @Test
    void webErrorRetainsStatusCodeMessageAndData() {
        WebError original = new WebError(409, "CONFLICT", "duplicate", "order-1");

        WebError restored = JsonKit.toObject(JsonKit.toJson(original), WebError.class);

        assertNotNull(restored);
        assertEquals(original, restored);
        assertEquals(409, restored.status());
    }

    @Test
    void readinessResponseRetainsBooleanState() {
        ReadinessResponse original = new ReadinessResponse(true);

        ReadinessResponse restored = JsonKit.toObject(JsonKit.toJson(original), ReadinessResponse.class);

        assertNotNull(restored);
        assertEquals(original, restored);
        assertEquals(true, restored.ready());
    }
}
