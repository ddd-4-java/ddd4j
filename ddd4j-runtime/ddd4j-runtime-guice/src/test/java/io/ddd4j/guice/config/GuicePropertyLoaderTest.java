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
package io.ddd4j.guice.config;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuicePropertyLoaderTest {

    @Test
    void loadsDefaultConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader());

        assertEquals("from-default",
                injector.getInstance(Key.get(String.class, Names.named("ddd4j.test.key"))));
    }

    @Test
    void loadsCustomConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader("custom-config.properties"));

        assertEquals("custom-value",
                injector.getInstance(Key.get(String.class, Names.named("custom.key"))));
    }

    @Test
    void toleratesMissingConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader("no-such-file.properties"));

        assertNotNull(injector);
        assertThrows(com.google.inject.ConfigurationException.class,
                () -> injector.getInstance(Key.get(String.class, Names.named("missing.key"))));
    }
}
