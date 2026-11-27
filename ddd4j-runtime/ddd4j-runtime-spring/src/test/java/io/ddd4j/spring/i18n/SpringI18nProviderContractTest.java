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
package io.ddd4j.spring.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.extension.context.NestedMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>delegate.addMessage()</code>\u3001<code>LocaleContextHolder.getLocaleContext()</code>\u3001<code>LocaleContextHolder.setLocale()</code> \u9a8c\u8bc1 <code>SpringI18nProviderContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class SpringI18nProviderContractTest {

    /** \u9a8c\u8bc1<code>provider.getMessage("welcome", "Alice")</code> \u7b49\u4e8e <code>"\u6b22\u8fce Alice"</code>\uff0c<code>provider.getMessage("welcome", "Alice")</code> \u7b49\u4e8e <code>"Hello Alice"</code>\u3002 */
    @Test
    void resolvesMessageArgumentsUsingCurrentLocale() {
        StaticMessageSource delegate = new StaticMessageSource();
        delegate.addMessage("welcome", Locale.CHINA, "欢迎 {0}");
        delegate.addMessage("welcome", Locale.US, "Hello {0}");
        SpringI18nProvider provider = new SpringI18nProvider(new NestedMessageSource(delegate));
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        try {
            LocaleContextHolder.setLocale(Locale.CHINA);
            assertEquals("欢迎 Alice", provider.getMessage("welcome", "Alice"));
            LocaleContextHolder.setLocale(Locale.US);
            assertEquals("Hello Alice", provider.getMessage("welcome", "Alice"));
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }
}
