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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>ReadinessEndpoint</code> 在<code>constructor</code>等操作中的行为契约。
 */
class ReadinessEndpointTest {

    /**
     * 验证<code>response</code>非空，<code>response.ready()</code>为真，<code>response.httpStatus()</code> 等于 <code>200</code>。
     */
    @Test
    void readinessReportsReady() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> true);
        ReadinessResponse response = endpoint.readiness();
        assertNotNull(response);
        assertTrue(response.ready());
        assertEquals(200, response.httpStatus());
    }

    /**
     * 验证<code>response.ready()</code>为假，<code>response.httpStatus()</code> 等于 <code>503</code>。
     */
    @Test
    void readinessReportsUnready() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> false);
        ReadinessResponse response = endpoint.readiness();
        assertFalse(response.ready());
        assertEquals(503, response.httpStatus());
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void constructorRejectsNullSupplier() {
        assertThrows(NullPointerException.class, () -> new ReadinessEndpoint(null));
    }

    /**
     * 验证<code>ReadinessEndpoint.PATH</code> 等于 <code>"/-/ready"</code>。
     */
    @Test
    void exposesProbePath() {
        assertEquals("/-/ready", ReadinessEndpoint.PATH);
    }
}
