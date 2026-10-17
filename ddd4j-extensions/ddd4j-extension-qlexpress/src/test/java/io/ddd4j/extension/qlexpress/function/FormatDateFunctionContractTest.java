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

class FormatDateFunctionContractTest {

    private final QLExpressEngine engine = QLExpress.create();

    @Test
    void formatsJavaTimeValue() {
        LocalDateTime value = LocalDateTime.of(2026, 10, 18, 9, 30);
        Object result = engine.execute("formatDate(value, 'yyyy-MM-dd HH:mm')",
                Collections.singletonMap("value", value));

        assertEquals("2026-10-18 09:30", result);
    }

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
