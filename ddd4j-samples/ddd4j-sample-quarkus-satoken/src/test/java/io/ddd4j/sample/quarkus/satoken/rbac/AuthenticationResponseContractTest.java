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
package io.ddd4j.sample.quarkus.satoken.rbac;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 认证业务端点响应的三线行为契约。
 */
class AuthenticationResponseContractTest {

    /** \u9a8c\u8bc1<code>data.get("orderId")</code>\u4e3a\u7a7a\uff0c<code>data.get("paid")</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void paymentResponseKeepsOrderAndNullableId() {
        Map<String, Object> data = new AuthenticationResource().businessPayOrder(null).getData();
        assertEquals(Arrays.asList("orderId", "paid"), new ArrayList<>(data.keySet()));
        assertNull(data.get("orderId"));
        assertEquals(true, data.get("paid"));
    }
}
