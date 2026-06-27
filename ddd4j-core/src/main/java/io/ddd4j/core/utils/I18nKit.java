package io.ddd4j.core.utils;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 国际化工具（纯 Java，零框架依赖）
 * <p>
 * 使用 Java 标准 {@link ResourceBundle} 加载 i18n 资源。
 * 框架适配层（如 ddd4j-spring 的 {@code SpringI18nProvider}）可提供更强大的实现。
 *
 * @author wandl
 * @since 3.4.x
 */
public final class I18nKit {

    private I18nKit() {
    }

    /**
     * 获取 i18n 消息（纯 Java ResourceBundle 实现）
     *
     * @param key  消息 key
     * @param args 参数（支持 {0}, {1} 占位符）
     * @return 解析后的消息，找不到 key 时返回 key 本身
     */
    public static String get(String key, Object... args) {
        if (key == null) {
            return null;
        }
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", Locale.getDefault());
            String pattern = bundle.getString(key);
            if (args == null || args.length == 0) {
                return pattern;
            }
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
