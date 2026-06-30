package io.ddd4j.guice.core;

import io.ddd4j.core.context.I18nProvider;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Guice 实现的 i18n 提供者
 * <p>
 * 使用 Java 标准 {@link ResourceBundle} 加载 i18n 资源。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public class GuiceI18nProvider implements I18nProvider {

    @Override
    public String getMessage(String key, Object... args) {
        if (java.util.Objects.isNull(key)) {
            return null;
        }
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", Locale.getDefault());
            String pattern = bundle.getString(key);
            if (java.util.Objects.isNull(args) || args.length == 0) {
                return pattern;
            }
            return String.format(pattern, args);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
