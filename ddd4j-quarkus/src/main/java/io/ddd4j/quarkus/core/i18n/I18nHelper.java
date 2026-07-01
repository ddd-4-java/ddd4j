package io.ddd4j.quarkus.core.i18n;

import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quarkus 原生国际化辅助。
 */
@Slf4j
public final class I18nHelper {

    private static final String BUNDLE_PREFIX = "i18n/message";
    private static final Map<String, ResourceBundle> CACHE = new ConcurrentHashMap<>();
    private static volatile String defaultLang = "zh";

    private I18nHelper() {
    }

    public static String getDefaultLang() {
        return defaultLang;
    }

    public static void setDefaultLang(String lang) {
        if (StrKit.isNotEmpty(lang)) {
            defaultLang = lang;
        }
    }

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
