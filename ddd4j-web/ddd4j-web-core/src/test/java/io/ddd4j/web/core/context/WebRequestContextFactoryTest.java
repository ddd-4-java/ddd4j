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
package io.ddd4j.web.core.context;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebRequestContextFactoryTest {

    private static final RequestIdGenerator FIXED_ID = () -> "generated-request";

    @Test
    void createUsesRequestIdWhenProvided() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID,
                (f, r, a) -> "10.0.0.1");
        WebRequestContext context = factory.create(requestData("provided-id"));

        assertEquals("provided-id", context.requestId());
        assertEquals("10.0.0.1", context.clientIp());
    }

    @Test
    void createGeneratesRequestIdWhenBlank() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID,
                (f, r, a) -> null);
        WebRequestContext context = factory.create(requestData("  "));

        assertEquals("generated-request", context.requestId());
    }

    @Test
    void createRejectsBlankGeneratedRequestId() {
        WebRequestContextFactory factory = new WebRequestContextFactory(() -> "  ",
                (f, r, a) -> null);
        assertThrows(IllegalStateException.class, () -> factory.create(requestData(null)));
    }

    @Test
    void createRejectsNullData() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID, (f, r, a) -> null);
        assertThrows(NullPointerException.class, () -> factory.create(null));
    }

    @Test
    void defaultFactoryUsesDefaults() {
        WebRequestContextFactory factory = new WebRequestContextFactory();
        WebRequestContext context = factory.create(requestData(null));
        org.junit.jupiter.api.Assertions.assertFalse(context.requestId().trim().isEmpty());
    }

    private static WebRequestData requestData(String requestId) {
        return new WebRequestData(requestId, "t-1", "tenant-a", "Bearer x", Locale.CHINA,
                "1.2.3.4", null, "10.0.0.1", "GET", "/api");
    }
}
