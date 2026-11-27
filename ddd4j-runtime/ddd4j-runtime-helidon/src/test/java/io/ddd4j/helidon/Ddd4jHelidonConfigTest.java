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
package io.ddd4j.helidon;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 <code>Ddd4jHelidonConfig</code> \u5728<code>value</code>\u3001<code>constructor</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class Ddd4jHelidonConfigTest {

    /** \u7528\u4e8e\u9694\u79bb <code>Config</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Config config;

    /** \u9a8c\u8bc1<code>ddd4jConfig.value("ddd4j.test", String.class)</code> \u7b49\u4e8e <code>Optional.of("configured")</code>\u3002 */
    @Test
    void valueReturnsConfiguredValue() {
        when(config.getOptionalValue("ddd4j.test", String.class))
                .thenReturn(Optional.of("configured"));

        Ddd4jHelidonConfig ddd4jConfig = new Ddd4jHelidonConfig(config);

        assertEquals(Optional.of("configured"), ddd4jConfig.value("ddd4j.test", String.class));
    }

    /** \u9a8c\u8bc1<code>ddd4jConfig.value("missing", Integer.class)</code> \u7b49\u4e8e <code>Optional.empty()</code>\u3002 */
    @Test
    void valueReturnsEmptyWhenAbsent() {
        when(config.getOptionalValue("missing", Integer.class))
                .thenReturn(Optional.empty());

        Ddd4jHelidonConfig ddd4jConfig = new Ddd4jHelidonConfig(config);

        assertEquals(Optional.empty(), ddd4jConfig.value("missing", Integer.class));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> new Ddd4jHelidonConfig(null));
    }
}
