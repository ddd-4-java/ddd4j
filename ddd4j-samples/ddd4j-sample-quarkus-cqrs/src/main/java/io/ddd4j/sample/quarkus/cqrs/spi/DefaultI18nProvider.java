package io.ddd4j.sample.quarkus.cqrs.spi;

import io.ddd4j.core.i18n.I18nProvider;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Quarkus CQRS 示例使用的默认国际化提供器。
 */
@ApplicationScoped
public class DefaultI18nProvider implements I18nProvider {

    @Override
    public String getMessage(String key, Object... args) {
        return I18nProvider.DEFAULT.getMessage(key, args);
    }
}
