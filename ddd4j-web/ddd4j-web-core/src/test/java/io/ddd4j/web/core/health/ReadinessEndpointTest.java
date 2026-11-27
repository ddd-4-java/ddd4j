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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class ReadinessEndpointTest {

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.ready()</code>\u4e3a\u771f\uff0c<code>response.httpStatus()</code> \u7b49\u4e8e <code>200</code>\u3002 */
    @Test
    void readinessReportsReady() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> true);
        ReadinessResponse response = endpoint.readiness();
        assertNotNull(response);
        assertTrue(response.ready());
        assertEquals(200, response.httpStatus());
    }

    /** \u9a8c\u8bc1<code>response.ready()</code>\u4e3a\u5047\uff0c<code>response.httpStatus()</code> \u7b49\u4e8e <code>503</code>\u3002 */
    @Test
    void readinessReportsUnready() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> false);
        ReadinessResponse response = endpoint.readiness();
        assertFalse(response.ready());
        assertEquals(503, response.httpStatus());
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullSupplier() {
        assertThrows(NullPointerException.class, () -> new ReadinessEndpoint(null));
    }

    /** \u9a8c\u8bc1<code>ReadinessEndpoint.PATH</code> \u7b49\u4e8e <code>"/-/ready"</code>\u3002 */
    @Test
    void exposesProbePath() {
        assertEquals("/-/ready", ReadinessEndpoint.PATH);
    }
}
