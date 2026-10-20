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
package io.ddd4j.sample.quarkus.cqrs.order.web;

import io.ddd4j.core.api.R;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CQRS 读侧响应字段顺序契约。 */
class OrderCQRSQueryResponseContractTest {

    @Test
    void listKeepsDocumentedFieldOrder() {
        Response response = new OrderCQRSQueryResource(null).list(null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ((R<?>) response.getEntity()).getData();
        assertEquals(Arrays.asList("page", "pageSize", "note"), new ArrayList<>(data.keySet()));
        assertEquals(1, data.get("page"));
        assertEquals(10, data.get("pageSize"));
    }
}
