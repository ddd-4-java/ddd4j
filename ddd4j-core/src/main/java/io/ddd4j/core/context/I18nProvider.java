package io.ddd4j.core.context;

import io.ddd4j.kit.lang.StrKit;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 国际化提供者接口（策略模式）。
 * <p>
 * 各框架适配层提供实现：
 * <ul>
 *   <li>Spring: 基于 MessageSource</li>
 *   <li>Quarkus: 基于 CDI + ResourceBundle</li>
 *   <li>Guice: 基于 ResourceBundle</li>
 * </ul>
 * 默认实现返回原始 key。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface I18nProvider {

    /**
     * 默认实现：使用 Java ResourceBundle 加载 i18n/messages.properties。
     * 找不到 key 时返回原始 key（含占位符替换）。
     */
    I18nProvider DEFAULT = (key, args) -> {
        if (StrKit.isBlank(key)) {
            return null;
        }
        String pattern = key;
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", Locale.getDefault());
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            // 找不到资源文件，使用原始 key
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    };

    /**
     * 获取国际化消息
     *
     * @param key  消息 key
     * @param args 格式化参数
     * @return 国际化后的消息
     */
    String getMessage(String key, Object... args);
}
