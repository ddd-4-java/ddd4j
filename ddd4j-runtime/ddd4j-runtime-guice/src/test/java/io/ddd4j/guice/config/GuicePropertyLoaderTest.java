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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class GuicePropertyLoaderTest {

    /** \u9a8c\u8bc1<code>injector.getInstance(Key.get(String.class, Names.named("ddd4j.test.key")))</code> \u7b49\u4e8e <code>"from-default"</code>\u3002 */
    @Test
    void loadsDefaultConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader());

        assertEquals("from-default",
                injector.getInstance(Key.get(String.class, Names.named("ddd4j.test.key"))));
    }

    /** \u9a8c\u8bc1<code>injector.getInstance(Key.get(String.class, Names.named("custom.key")))</code> \u7b49\u4e8e <code>"custom-value"</code>\u3002 */
    @Test
    void loadsCustomConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader("custom-config.properties"));

        assertEquals("custom-value",
                injector.getInstance(Key.get(String.class, Names.named("custom.key"))));
    }

    /** \u9a8c\u8bc1<code>injector</code>\u975e\u7a7a\uff0c<code>injector.getInstance(Key.get(String.class, Names.named("missing.key")))</code> \u629b\u51fa <code>com.google.inject.ConfigurationException.class</code>\u3002 */
    @Test
    void toleratesMissingConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader("no-such-file.properties"));

        assertNotNull(injector);
        assertThrows(com.google.inject.ConfigurationException.class,
                () -> injector.getInstance(Key.get(String.class, Names.named("missing.key"))));
    }
}
