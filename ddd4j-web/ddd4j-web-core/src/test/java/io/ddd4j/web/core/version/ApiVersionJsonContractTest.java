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
package io.ddd4j.web.core.version;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 三线共同的 API 版本 JSON 字符串契约。
 */
class ApiVersionJsonContractTest {

    @Test
    void serializesAndDeserializesAsNormalizedString() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ApiVersion version = ApiVersion.parse("2.1");

        assertEquals("\"2.1.0\"", mapper.writeValueAsString(version));
        assertEquals(version, mapper.readValue("\"2.1.0\"", ApiVersion.class));
    }
}
