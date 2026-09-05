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
package io.ddd4j.web.core.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessEndpointTest {

    @Test
    void readinessReportsReady() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> true);
        ReadinessResponse response = endpoint.readiness();
        assertNotNull(response);
        assertTrue(response.ready());
        assertEquals(200, response.httpStatus());
    }

    @Test
    void readinessReportsUnready() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> false);
        ReadinessResponse response = endpoint.readiness();
        assertFalse(response.ready());
        assertEquals(503, response.httpStatus());
    }

    @Test
    void constructorRejectsNullSupplier() {
        assertThrows(NullPointerException.class, () -> new ReadinessEndpoint(null));
    }

    @Test
    void exposesProbePath() {
        assertEquals("/-/ready", ReadinessEndpoint.PATH);
    }
}
