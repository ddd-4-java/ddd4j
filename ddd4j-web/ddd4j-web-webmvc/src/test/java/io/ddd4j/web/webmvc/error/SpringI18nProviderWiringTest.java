package io.ddd4j.web.webmvc.error;

import io.ddd4j.spring.i18n.SpringI18nProvider;
import io.ddd4j.web.webmvc.config.MessageSourceConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.extension.context.NestedMessageSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 三线 WebMVC 消息源与 Spring 运行时国际化提供者的装配契约。
 */
class SpringI18nProviderWiringTest {

    @Test
    void injectsExtensionMessageSourceIntoSpringI18nProvider() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            StaticMessageSource delegate = new StaticMessageSource();
            delegate.addMessage("welcome", Locale.getDefault(), "欢迎");
            context.registerBean(NestedMessageSource.class, () -> new NestedMessageSource(delegate));
            context.registerBean(SpringI18nProvider.class);
            context.refresh();
            assertEquals("欢迎", context.getBean(SpringI18nProvider.class).getMessage("welcome"));
        }
    }

    @Test
    void resolvesRegisteredSpringExtensionMessageSource() {
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            StaticMessageSource delegate = new StaticMessageSource();
            delegate.addMessage("welcome", Locale.FRANCE, "Bonjour {0}");
            context.registerBean(NestedMessageSource.class, () -> new NestedMessageSource(delegate));
            context.registerBean(SpringI18nProvider.class);
            context.refresh();

            LocaleContextHolder.setLocale(Locale.FRANCE);
            assertEquals("Bonjour DDD", context.getBean(SpringI18nProvider.class).getMessage("welcome", "DDD"));
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }

    @Test
    void usesAMutableDefaultBasenameListPerConfiguration() {
        MessageSourceConfigurationProperties properties = new MessageSourceConfigurationProperties();
        assertEquals(Collections.singletonList("messages"), properties.getBasename());

        properties.getBasename().add("orders");

        assertEquals(Arrays.asList("messages", "orders"), properties.getBasename());
        assertEquals(Collections.singletonList("messages"), new MessageSourceConfigurationProperties().getBasename());
    }
}
