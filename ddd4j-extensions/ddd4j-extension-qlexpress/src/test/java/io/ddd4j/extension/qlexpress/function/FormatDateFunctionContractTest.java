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
package io.ddd4j.extension.qlexpress.function;

import io.ddd4j.extension.qlexpress.QLExpress;
import io.ddd4j.extension.qlexpress.QLExpressEngine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>engine.execute()</code>\u3001<code>Collections.singletonMap()</code>\u3001<code>Date.from()</code> \u9a8c\u8bc1 <code>FormatDateFunctionContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class FormatDateFunctionContractTest {

    /** \u5f53\u524d\u7528\u4f8b\u8c03\u7528\u7684\u8868\u8fbe\u5f0f\u6216\u4e1a\u52a1\u6267\u884c\u5f15\u64ce\u3002 */
    private final QLExpressEngine engine = QLExpress.create();

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"2026-10-18 09:30"</code>\u3002 */
    @Test
    void formatsJavaTimeValue() {
        LocalDateTime value = LocalDateTime.of(2026, 10, 18, 9, 30);
        Object result = engine.execute("formatDate(value, 'yyyy-MM-dd HH:mm')",
                Collections.singletonMap("value", value));

        assertEquals("2026-10-18 09:30", result);
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void formatsLegacyDateUsingSystemZone() {
        Date value = Date.from(Instant.parse("2026-10-18T01:30:00Z"));
        String expected = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(value.toInstant().atZone(ZoneId.systemDefault()));
        Object result = engine.execute("formatDate(value, 'yyyy-MM-dd HH:mm')",
                Collections.singletonMap("value", value));

        assertEquals(expected, result);
    }
}
