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

import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestFailure;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.health.ReadinessResponse;
import io.ddd4j.web.core.idempotency.IdempotencyLease;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebValueSemanticsTest {

    @Test
    void equivalentRequestDataCanFindTheSameRequestEntry() {
        WebRequestData first = new WebRequestData("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");
        WebRequestData equivalent = new WebRequestData("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");
        WebRequestData otherPath = new WebRequestData("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/payments");
        Map<WebRequestData, String> requests = new HashMap<>();
        requests.put(first, "seen");

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertEquals("seen", requests.get(equivalent));
        assertNotEquals(first, otherPath);
        assertNotEquals(first, "request-1");
        assertTrue(first.toString().startsWith("WebRequestData["));
    }

    @Test
    void normalizedRequestContextHasStableValueIdentity() {
        WebRequestContext first = new WebRequestContext("request-1", null, "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "post", "/orders");
        WebRequestContext equivalent = new WebRequestContext("request-1", "request-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "POST", "/orders");
        WebRequestContext otherTenant = new WebRequestContext("request-1", null, "tenant-2", "Bearer token",
                Locale.CHINA, "203.0.113.1", "post", "/orders");

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, otherTenant);
        assertNotEquals(first, null);
        assertTrue(first.toString().startsWith("WebRequestContext["));
    }

    @Test
    void requestFailuresRetainTheOriginalCauseAsPartOfIdentity() {
        IllegalStateException cause = new IllegalStateException("downstream failed");
        WebRequestFailure first = new WebRequestFailure("POST", "/orders", cause);
        WebRequestFailure equivalent = new WebRequestFailure("POST", "/orders", cause);
        WebRequestFailure otherCause = new WebRequestFailure("POST", "/orders",
                new IllegalStateException("downstream failed"));

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, otherCause);
        assertEquals(cause, first.getCause());
        assertTrue(first.toString().startsWith("WebRequestFailure["));
    }

    @Test
    void errorValueDistinguishesHttpStatus() {
        WebError first = new WebError(422, "VALIDATION", "bad order", "detail");
        WebError equivalent = new WebError(422, "VALIDATION", "bad order", "detail");
        WebError otherStatus = new WebError(400, "VALIDATION", "bad order", "detail");

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, otherStatus);
        assertTrue(first.toString().contains("VALIDATION"));
    }

    @Test
    void leaseOwnerIsPartOfIdempotencyIdentity() {
        IdempotencyLease first = new IdempotencyLease("payment-1", "owner-1", Duration.ofMinutes(1));
        IdempotencyLease equivalent = new IdempotencyLease("payment-1", "owner-1", Duration.ofMinutes(1));
        IdempotencyLease otherOwner = new IdempotencyLease("payment-1", "owner-2", Duration.ofMinutes(1));
        Map<IdempotencyLease, String> leases = new HashMap<>();
        leases.put(first, "acquired");

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertEquals("acquired", leases.get(equivalent));
        assertNotEquals(first, otherOwner);
        assertEquals("owner-1", first.getOwnerToken());
        assertTrue(first.toString().startsWith("IdempotencyLease["));
    }

    @Test
    void readinessValueDistinguishesHealthyAndUnhealthyResponses() {
        ReadinessResponse ready = new ReadinessResponse(true);
        ReadinessResponse equivalent = new ReadinessResponse(true);
        ReadinessResponse notReady = new ReadinessResponse(false);

        assertEquals(ready, equivalent);
        assertEquals(ready.hashCode(), equivalent.hashCode());
        assertNotEquals(ready, notReady);
        assertTrue(ready.isReady());
        assertFalse(notReady.isReady());
        assertTrue(ready.toString().contains("ready=true"));
    }
}
