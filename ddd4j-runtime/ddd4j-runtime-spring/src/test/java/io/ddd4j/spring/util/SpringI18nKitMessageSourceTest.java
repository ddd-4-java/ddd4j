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

    /** \u9a8c\u8bc1<code>I18nKit.getMessage("welcome", new String[]{"Alice"})</code> \u7b49\u4e8e <code>"\u6b22\u8fce Alice"</code>\u3002 */
    @Test
    void resolvesExtensionSourceWithoutLegacyBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NestedMessageSource.class, () -> extensionSource("欢迎 {0}"));
            context.refresh();
            withContext(context, () -> assertEquals("欢迎 Alice", I18nKit.getMessage("welcome", new String[]{"Alice"})));
        }
    }

    /** \u9a8c\u8bc1<code>I18nKit.getMessage("welcome")</code> \u7b49\u4e8e <code>"\u65b0\u7248"</code>\uff0c<code>I18nKit.getMessage("missing", null, "\u7f3a\u7701")</code> \u7b49\u4e8e <code>"\u7f3a\u7701"</code>\u3002 */
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

    /**
     * \u521b\u5efa <code>new NestedMessageSource</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new NestedMessageSource(delegate)</code>
     */
    private static NestedMessageSource extensionSource(String message) {
        StaticMessageSource delegate = new StaticMessageSource();
        delegate.addMessage("welcome", Locale.getDefault(), message);
        return new NestedMessageSource(delegate);
    }

    /**
     * \u8c03\u7528 <code>SpringContextUtils.getContext()</code>\u3001<code>current.setApplicationContext()</code>\u3001<code>assertion.run()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u4fdd\u5b58\u8bf7\u6c42\u6216\u8fd0\u884c\u65f6\u72b6\u6001\u7684\u4e0a\u4e0b\u6587
     * @param assertion \u4f20\u7ed9 <code>assertion.run()</code> \u7684 <code>Runnable</code> \u8f93\u5165
     */
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
