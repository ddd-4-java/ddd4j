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

    /** \u9a8c\u8bc1<code>context.getBean(SpringI18nProvider.class).getMessage("welcome")</code> \u7b49\u4e8e <code>"\u6b22\u8fce"</code>\u3002 */
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
