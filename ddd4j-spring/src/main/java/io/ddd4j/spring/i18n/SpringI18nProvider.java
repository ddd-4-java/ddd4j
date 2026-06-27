package io.ddd4j.spring.i18n;

import io.ddd4j.core.context.I18nProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Spring 实现的国际化提供者
 * <p>
 * 基于 Spring MessageSource 实现。
 *
 * @author wandl
 */
@Slf4j
@Component
public class SpringI18nProvider implements I18nProvider {

    private final MessageSource messageSource;

    public SpringI18nProvider(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public String getMessage(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, locale);
    }
}
