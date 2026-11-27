package io.ddd4j.guice.i18n;

import io.ddd4j.core.i18n.I18nProvider;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Guice 实现的 i18n 提供者
 * <p>
 * 使用 Java 标准 {@link ResourceBundle} 加载 i18n 资源。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class GuiceI18nProvider implements I18nProvider {

    /**
     * 按提供的条件读取或解析消息文本。
     *
     * <p>此实现调用 <code>ResourceBundle.getBundle("i18n/messages", Locale.getDefault())</code>。</p>
     *
     * @param key 消息 key
     * @param args 格式化参数
     * @return 根据执行路径返回<code>null</code>、<code>pattern</code>、<code>String.format(pattern, args)</code>
     */
    @Override
    public String getMessage(String key, Object... args) {
        if (Objects.isNull(key)) {
            return null;
        }
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", Locale.getDefault());
            String pattern = bundle.getString(key);
            if (Objects.isNull(args) || args.length == 0) {
                return pattern;
            }
            return String.format(pattern, args);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
