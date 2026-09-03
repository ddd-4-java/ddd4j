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
package io.ddd4j.core.util;

import io.ddd4j.core.i18n.I18nProvider;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国际化工具类（纯 Java 实现）。
 * <p>
 * 使用策略模式，通过 {@link I18nProvider} 接口实现框架无关的国际化。
 * 各框架适配层在启动时注册对应的 I18nProvider 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class I18nKit {

    private static final Map<String, I18nProvider> LOCALE_PROVIDERS = new ConcurrentHashMap<>();
    private static volatile I18nProvider provider = I18nProvider.DEFAULT;

    private I18nKit() {
    }

    /**
     * 注册全局 I18nProvider
     *
     * @param provider I18nProvider 实现
     */
    public static void register(I18nProvider provider) {
        if (Objects.nonNull(provider)) {
            I18nKit.provider = provider;
        }
    }

    /**
     * 获取当前全局国际化提供者，供运行时在关闭时恢复先前状态。
     *
     * @return 当前全局国际化提供者
     */
    public static I18nProvider getProvider() {
        return provider;
    }

    /**
     * 注册指定 Locale 的 I18nProvider
     *
     * @param locale   Locale
     * @param provider I18nProvider 实现
     */
    public static void register(Locale locale, I18nProvider provider) {
        if (Objects.nonNull(locale) && Objects.nonNull(provider)) {
            LOCALE_PROVIDERS.put(locale.toString(), provider);
        }
    }

    /**
     * 获取国际化消息
     *
     * @param key  消息 key
     * @param args 格式化参数
     * @return 国际化后的消息
     */
    public static String get(String key, Object... args) {
        return provider.getMessage(key, args);
    }

    /**
     * 获取指定 Locale 的国际化消息
     *
     * @param locale Locale
     * @param key    消息 key
     * @param args   格式化参数
     * @return 国际化后的消息
     */
    public static String get(Locale locale, String key, Object... args) {
        if (Objects.nonNull(locale)) {
            I18nProvider localeProvider = LOCALE_PROVIDERS.get(locale.toString());
            if (Objects.nonNull(localeProvider)) {
                return localeProvider.getMessage(key, args);
            }
        }
        return provider.getMessage(key, args);
    }
}
