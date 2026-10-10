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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiVersion} 解析形态、比较语义与值对象契约测试。
 *
 * <p>对应规格 Scenario：次版本数值比较、缺失段补零等价。
 */
class ApiVersionTest {

    @Test
    void parseAcceptsCommonForms() {
        assertEquals("2.0.0", ApiVersion.parse("v2").toString());
        assertEquals("2.0.0", ApiVersion.parse("2").toString());
        assertEquals("2.1.0", ApiVersion.parse("2.1").toString());
        assertEquals("2.1.0", ApiVersion.parse("2.1.0").toString());
        assertEquals("2.1.0", ApiVersion.parse("  2.1  ").toString());
        assertEquals(ApiVersion.of(2, 1, 0), ApiVersion.parse("2.1"));
        ApiVersion parsed = ApiVersion.parse("V3.7.2");
        assertEquals(3, parsed.major());
        assertEquals(7, parsed.minor());
        assertEquals(2, parsed.patch());
    }

    @Test
    void missingSegmentsAreZeroPadded() {
        //规格 Scenario：缺失段补零等价（2.1 == 2.1.0）。
        assertEquals(ApiVersion.parse("2.1.0"), ApiVersion.parse("2.1"));
        assertEquals(ApiVersion.parse("2.1.0"), ApiVersion.parse("v2.1"));
        assertEquals(ApiVersion.parse("2.0.0"), ApiVersion.parse("2"));
        assertEquals(ApiVersion.parse("2.0.0"), ApiVersion.parse("v2"));
        assertEquals(0, ApiVersion.parse("2.1").patch());
        assertEquals(0, ApiVersion.parse("2").minor());
        assertEquals(ApiVersion.parse("2.1.0").hashCode(), ApiVersion.parse("2.1").hashCode());
    }

    @Test
    void minorVersionsCompareNumerically() {
        //规格 Scenario：次版本数值比较（2.10.0 > 2.9.0，非字典序）。
        assertTrue(ApiVersion.parse("2.10.0").compareTo(ApiVersion.parse("2.9.0")) > 0);
        assertTrue(ApiVersion.parse("2.9.1").compareTo(ApiVersion.parse("2.10.0")) < 0);
        assertTrue(ApiVersion.parse("2.0.1").compareTo(ApiVersion.parse("2.1.0")) < 0);
        assertTrue(ApiVersion.parse("3.0.0").compareTo(ApiVersion.parse("2.9.9")) > 0);
        assertEquals(0, ApiVersion.parse("2.10.0").compareTo(ApiVersion.parse("2.10.0")));
    }

    @Test
    void rejectsIllegalForms() {
        String[] illegals = {"", "  ", "2.x", "vx", "v", "1.2.3.4", "-1", "2..1", "abc", "2.1.", "+2", "9999999999999"};
        for (String illegal : illegals) {
            assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse(illegal),
                    "expected rejection for: [" + illegal + "]");
        }
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse(null));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.of(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.of(1, -1, 0));
    }

    @Test
    void equalsHashCodeAndCompatibility() {
        ApiVersion twoOne = ApiVersion.parse("2.1");
        ApiVersion twoOneFull = ApiVersion.parse("2.1.0");
        assertEquals(twoOne, twoOne);
        assertEquals(twoOne, twoOneFull);
        assertEquals(twoOne.hashCode(), twoOneFull.hashCode());
        assertNotEquals(twoOne, ApiVersion.parse("2.2.0"));
        assertNotEquals(twoOne, "2.1.0");

        assertTrue(twoOne.isCompatibleWith(ApiVersion.parse("2.9.0")));
        assertTrue(ApiVersion.parse("2.9.0").isCompatibleWith(twoOne));
        assertFalse(twoOne.isCompatibleWith(ApiVersion.parse("1.9.0")));
        assertFalse(twoOne.isCompatibleWith(null));
    }

    @Test
    void compareToOrdersBySegmentInSequence() {
        assertTrue(ApiVersion.parse("1.9.9").compareTo(ApiVersion.parse("2.0.0")) < 0);
        assertTrue(ApiVersion.of(2, 0, 1).compareTo(ApiVersion.parse("2.0.0")) > 0);
        assertThrows(NullPointerException.class, () -> ApiVersion.parse("1.0.0").compareTo(null));
    }
}
