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
package io.ddd4j.guice.i18n;

import io.ddd4j.core.i18n.I18nProvider;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Guice 实现的 i18n 提供者
 * <p>
 * 使用 Java 标准 {@link ResourceBundle} 加载 i18n 资源。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class GuiceI18nProvider implements I18nProvider {

    @Override
    public String getMessage(String key, Object... args) {
        if (Objects.isNull(key)) {
            return null;
        }
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", Locale.getDefault());
            String pattern = bundle.getString(key);
            if (Objects.isNull(args) || args.length == 0) {
                return pattern;
            }
            return String.format(pattern, args);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
