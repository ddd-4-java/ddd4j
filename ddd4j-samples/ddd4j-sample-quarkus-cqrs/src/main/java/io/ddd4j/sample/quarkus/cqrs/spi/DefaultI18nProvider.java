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
package io.ddd4j.sample.quarkus.cqrs.spi;

import io.ddd4j.core.i18n.I18nProvider;
import javax.enterprise.context.ApplicationScoped;

/**
 * Quarkus CQRS 示例使用的默认国际化提供器。
 */
@ApplicationScoped
public class DefaultI18nProvider implements I18nProvider {

    /**
     * 按提供的条件读取或解析消息文本。
     *
     * <p>此实现调用 <code>I18nProvider.DEFAULT.getMessage(key, args)</code>。</p>
     *
     * @param key 消息 key
     * @param args 格式化参数
     * @return 实际返回<code>I18nProvider.DEFAULT.getMessage(key, args)</code>
     */
    @Override
    public String getMessage(String key, Object... args) {
        return I18nProvider.DEFAULT.getMessage(key, args);
    }
}
