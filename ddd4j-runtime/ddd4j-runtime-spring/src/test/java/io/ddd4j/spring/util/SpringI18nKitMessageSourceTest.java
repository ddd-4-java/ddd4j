package io.ddd4j.spring.util;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.extension.context.SpringContext;
import org.springframework.extension.context.SpringContextAwareContext;
import org.springframework.extension.utils.SpringContextUtils;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 静态国际化入口与本线 Easy4J 消息源的共同契约。
 */
class SpringI18nKitMessageSourceTest {

    /**
     * 创建 <code>new NestedMessageSource</code> 供当前测试路径调用。
     *
     * @param message 测试传递或用于诊断的消息
     * @return 当前测试使用的结果，取自 <code>new NestedMessageSource(delegate)</code>
     */
    private static NestedMessageSource extensionSource(String message) {
        StaticMessageSource delegate = new StaticMessageSource();
        delegate.addMessage("welcome", Locale.getDefault(), message);
        return new NestedMessageSource(delegate);
    }

    /**
     * 调用 <code>SpringContextUtils.getContext()</code>、<code>current.setApplicationContext()</code>、<code>assertion.run()</code> 执行当前夹具操作。
     *
     * @param context   当前框架保存请求或运行时状态的上下文
     * @param assertion 传给 <code>assertion.run()</code> 的 <code>Runnable</code> 输入
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

    /**
     * 验证<code>I18nKit.getMessage("welcome", new String[]{"Alice"})</code> 等于 <code>"欢迎 Alice"</code>。
     */
    @Test
    void resolvesExtensionSourceWithoutLegacyBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NestedMessageSource.class, () -> extensionSource("欢迎 {0}"));
            context.refresh();
            withContext(context, () -> assertEquals("欢迎 Alice", I18nKit.getMessage("welcome", new String[]{"Alice"})));
        }
    }

    /**
     * 验证<code>I18nKit.getMessage("welcome")</code> 等于 <code>"新版"</code>，<code>I18nKit.getMessage("missing", null, "缺省")</code> 等于 <code>"缺省"</code>。
     */
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
}
