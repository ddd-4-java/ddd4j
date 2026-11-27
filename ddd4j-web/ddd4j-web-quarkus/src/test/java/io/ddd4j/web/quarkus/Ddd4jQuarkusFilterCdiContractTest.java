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
package io.ddd4j.web.quarkus;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u901a\u8fc7 <code>Arc.container()</code> \u9a8c\u8bc1 <code>Ddd4jQuarkusFilterCdiContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@QuarkusTest
class Ddd4jQuarkusFilterCdiContractTest {

    /** \u9a8c\u8bc1<code>Arc.container().instance(Ddd4jQuarkusWebFilter.class).isAvailable()</code>\u4e3a\u771f\u3002 */
    @Test
    void filterIsDiscoveredByCdi() {
        assertTrue(Arc.container().instance(Ddd4jQuarkusWebFilter.class).isAvailable());
    }
}
