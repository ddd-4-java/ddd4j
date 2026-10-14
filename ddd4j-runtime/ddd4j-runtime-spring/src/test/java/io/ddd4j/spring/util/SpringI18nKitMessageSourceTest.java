package io.ddd4j.spring.util;

import org.junit.jupiter.api.Test;
import org.springframework.extension.context.SpringContext;
import org.springframework.extension.context.SpringContextAwareContext;
import org.springframework.extension.utils.SpringContextUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.extension.context.NestedMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 静态国际化入口与本线 Easy4J 消息源的共同契约。 */
class SpringI18nKitMessageSourceTest {

    @Test
    void resolvesExtensionSourceWithoutLegacyBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NestedMessageSource.class, () -> extensionSource("欢迎 {0}"));
            context.refresh();
            withContext(context, () -> assertEquals("欢迎 Alice", I18nKit.getMessage("welcome", new String[]{"Alice"})));
        }
    }

    @Test
    void preservesDefaultMessage() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("extensionMessageSource", NestedMessageSource.class, () -> extensionSource("新版"));
            context.refresh();
            withContext(context, () -> {
                assertEquals("新版", I18nKit.getMessage("welcome"));
                assertEquals("缺省", I18nKit.getMessage("missing", null, "缺省"));
            });
        }
    }

    private static NestedMessageSource extensionSource(String message) {
        StaticMessageSource delegate = new StaticMessageSource();
        delegate.addMessage("welcome", Locale.getDefault(), message);
        return new NestedMessageSource(delegate);
    }

    private static void withContext(AnnotationConfigApplicationContext context, Runnable assertion) {
        SpringContext previous = SpringContextUtils.getContext();
        SpringContextAwareContext current = new SpringContextAwareContext();
        current.setApplicationContext(context);
        try {
            assertion.run();
        } finally {
            SpringContextUtils.setContext(previous);
        }
    }
}
