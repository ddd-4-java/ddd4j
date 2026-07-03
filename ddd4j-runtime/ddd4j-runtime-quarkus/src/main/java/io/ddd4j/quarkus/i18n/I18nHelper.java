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
 * @since 2.0.x
 */
@Slf4j
public final class I18nHelper {

    /** 国际化资源文件前缀 */
    private static final String BUNDLE_PREFIX = "i18n/message";
    /** 国际化 ResourceBundle 缓存（key=语言标签） */
    private static final Map<String, ResourceBundle> CACHE = new ConcurrentHashMap<>();
    @Getter
    /** 默认语言（zh） */
    private static volatile String defaultLang = "zh";

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
