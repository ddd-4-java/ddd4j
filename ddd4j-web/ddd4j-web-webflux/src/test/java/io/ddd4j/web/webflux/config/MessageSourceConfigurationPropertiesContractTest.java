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
package io.ddd4j.web.webflux.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线共同的消息源配置值契约。
 */
class MessageSourceConfigurationPropertiesContractTest {

    /** \u9a8c\u8bc1<code>first.getBasename()</code> \u7b49\u4e8e <code>Arrays.asList("messages")</code>\uff0c<code>first.getEncoding()</code> \u7b49\u4e8e <code>StandardCharsets.UTF_8</code>\uff0c<code>second.getBasename()</code> \u7b49\u4e8e <code>Arrays.asList("messages")</code>\u3002 */
    @Test
    void defaultsAreIndependentAndMutablePerInstance() {
        MessageSourceConfigurationProperties first = new MessageSourceConfigurationProperties();
        MessageSourceConfigurationProperties second = new MessageSourceConfigurationProperties();

        assertEquals(Arrays.asList("messages"), first.getBasename());
        assertEquals(StandardCharsets.UTF_8, first.getEncoding());
        assertTrue(first.isFallbackToSystemLocale());
        assertNull(first.getCacheDuration());
        assertFalse(first.isAlwaysUseMessageFormat());
        assertFalse(first.isUseCodeAsDefaultMessage());

        first.getBasename().add("errors");
        assertEquals(Arrays.asList("messages", "errors"), first.getBasename());
        assertEquals(Arrays.asList("messages"), second.getBasename());
    }

    /** \u9a8c\u8bc1<code>properties.getBasename()</code> \u7b49\u4e8e <code>Arrays.asList("custom")</code>\uff0c<code>properties.getCacheDuration()</code> \u7b49\u4e8e <code>Duration.ofMinutes(2)</code>\uff0c<code>properties.isUseCodeAsDefaultMessage()</code>\u4e3a\u771f\u3002 */
    @Test
    void settersReplaceConfiguredValues() {
        MessageSourceConfigurationProperties properties = new MessageSourceConfigurationProperties();
        properties.setBasename(Arrays.asList("custom"));
        properties.setCacheDuration(Duration.ofMinutes(2));
        properties.setFallbackToSystemLocale(false);
        properties.setAlwaysUseMessageFormat(true);
        properties.setUseCodeAsDefaultMessage(true);

        assertEquals(Arrays.asList("custom"), properties.getBasename());
        assertEquals(Duration.ofMinutes(2), properties.getCacheDuration());
        assertFalse(properties.isFallbackToSystemLocale());
        assertTrue(properties.isAlwaysUseMessageFormat());
        assertTrue(properties.isUseCodeAsDefaultMessage());
    }
}
