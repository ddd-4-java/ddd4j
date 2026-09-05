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

@ExtendWith(MockitoExtension.class)
class Ddd4jHelidonConfigTest {

    @Mock
    private Config config;

    @Test
    void valueReturnsConfiguredValue() {
        when(config.getOptionalValue("ddd4j.test", String.class))
                .thenReturn(Optional.of("configured"));

        Ddd4jHelidonConfig ddd4jConfig = new Ddd4jHelidonConfig(config);

        assertEquals(Optional.of("configured"), ddd4jConfig.value("ddd4j.test", String.class));
    }

    @Test
    void valueReturnsEmptyWhenAbsent() {
        when(config.getOptionalValue("missing", Integer.class))
                .thenReturn(Optional.empty());

        Ddd4jHelidonConfig ddd4jConfig = new Ddd4jHelidonConfig(config);

        assertEquals(Optional.empty(), ddd4jConfig.value("missing", Integer.class));
    }

    @Test
    void constructorRejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> new Ddd4jHelidonConfig(null));
    }
}
