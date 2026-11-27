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
package io.ddd4j.quarkus.i18n;

import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quarkus 原生国际化辅助。
 * <p>
 * 提供基于 {@link ResourceBundle} 的国际化消息解析能力，
 * 支持多语言回退、参数格式化及默认语言设置。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@Slf4j
public final class I18nHelper {

    /**
     * 国际化资源文件前缀
     */
    private static final String BUNDLE_PREFIX = "i18n/message";
    /**
     * 国际化 ResourceBundle 缓存（key=语言标签）
     */
    private static final Map<String, ResourceBundle> CACHE = new ConcurrentHashMap<>();
    /** \u8c03\u7528\u672a\u6307\u5b9a\u8bed\u8a00\u65f6\u4f7f\u7528\u7684\u9ed8\u8ba4\u8bed\u8a00\u6807\u8bb0\u3002 */
    @Getter
    /** 默认语言（zh） */
    private static volatile String defaultLang = "zh";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>I18nHelper</code> \u5b9e\u4f8b\u3002 */
    private I18nHelper() {
    }

    /**
     * 设置默认语言。
     *
     * @param lang 语言标签（如 zh、en）
     */
    public static void setDefaultLang(String lang) {
        if (StrKit.isNotEmpty(lang)) {
            defaultLang = lang;
        }
    }

    /**
     * 获取国际化消息。
     *
     * @param lang       语言标签
     * @param key        消息键值
     * @param parameters 格式化参数
     * @return 国际化字符串
     */
    public static String i18n(String lang, String key, Object... parameters) {
        if (StrKit.isEmpty(key)) {
            return "";
        }
        if (StrKit.isEmpty(lang)) {
            lang = defaultLang;
        }

        String result = resolve(lang, key);
        if (Objects.nonNull(parameters) && parameters.length > 0) {
            try {
                result = String.format(result, parameters);
            } catch (Exception ex) {
                log.warn("Could not format i18n msg, lang={}, key={}", lang, key);
            }
        }
        return result;
    }

    /**
     * \u8bfb\u53d6\u76ee\u6807\u8bed\u8a00\u7684\u6d88\u606f\u8d44\u6e90\u5e76\u6309\u6d88\u606f\u952e\u67e5\u627e\u6587\u672c\u3002
     * @param lang \u6d88\u606f\u4f7f\u7528\u7684\u8bed\u8a00\u6807\u8bb0
     * @param key \u5bb9\u5668\u7ed1\u5b9a\u952e\u6216\u5f85\u67e5\u627e\u7684\u6d88\u606f\u952e
     * @return \u89e3\u6790\u5f97\u5230\u7684\u672c\u5730\u5316\u6d88\u606f
     */
    private static String resolve(String lang, String key) {
        ResourceBundle bundle = CACHE.computeIfAbsent(lang, I18nHelper::loadBundle);
        if (Objects.nonNull(bundle) && bundle.containsKey(key)) {
            return bundle.getString(key);
        }
        if (lang.contains("-")) {
            String fallback = lang.substring(0, lang.indexOf('-'));
            ResourceBundle fallbackBundle = CACHE.computeIfAbsent(fallback, I18nHelper::loadBundle);
            if (Objects.nonNull(fallbackBundle) && fallbackBundle.containsKey(key)) {
                return fallbackBundle.getString(key);
            }
        }
        return key;
    }

    /**
     * \u4e3a\u6307\u5b9a\u8bed\u8a00\u52a0\u8f7d\u914d\u7f6e\u7684\u56fd\u9645\u5316\u8d44\u6e90\u3002
     * @param lang \u6d88\u606f\u4f7f\u7528\u7684\u8bed\u8a00\u6807\u8bb0
     * @return \u8be5\u8bed\u8a00\u4f7f\u7528\u7684\u8d44\u6e90\u5305
     */
    private static ResourceBundle loadBundle(String lang) {
        try {
            Locale locale = Locale.forLanguageTag(lang);
            return ResourceBundle.getBundle(BUNDLE_PREFIX, locale);
        } catch (MissingResourceException ex) {
            log.debug("i18n bundle not found for lang={}", lang);
            return null;
        }
    }
}
