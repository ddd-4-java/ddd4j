package io.ddd4j.sample.javalin.cqrs.spi;

import io.ddd4j.core.i18n.I18nProvider;

/**
 * 国际化提供者：直接复用 {@link I18nProvider#DEFAULT}（基于 ResourceBundle）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class DefaultI18nProvider implements I18nProvider {

    @Override
    public String getMessage(String key, Object... args) {
        return I18nProvider.DEFAULT.getMessage(key, args);
    }
}