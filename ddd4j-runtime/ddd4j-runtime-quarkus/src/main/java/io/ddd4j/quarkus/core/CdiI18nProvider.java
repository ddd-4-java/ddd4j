package io.ddd4j.quarkus.core;

import java.util.Objects;

import io.ddd4j.core.context.I18nProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Quarkus CDI 实现的 i18n 提供者
 * <p>
 * 使用 Java 标准 {@link ResourceBundle} 加载 i18n 资源。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
public class CdiI18nProvider implements I18nProvider {

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
