package io.ddd4j.web.webmvc.error;

import io.ddd4j.spring.i18n.SpringI18nProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.extension.context.NestedMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 三线 WebMVC 消息源与 Spring 运行时国际化提供者的装配契约。 */
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

}
