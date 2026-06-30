package io.ddd4j.extension.pf4j.util;

import io.ddd4j.extension.pf4j.annotation.ExtensionMapping;
import io.ddd4j.extension.pf4j.exception.PluginInvokeException;
import org.pf4j.PluginManager;

import java.util.List;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Pf4jKit {

    public static <T> T getExtensionPoint(PluginManager pluginManager, Class<T> type, String pluginId,
                                          String extensionId) throws PluginInvokeException {
        if (hasText(pluginId) && hasText(extensionId)) {
            List<T> extensions = pluginManager.getExtensions(type, pluginId);
            for (T extension : extensions) {
                ExtensionMapping em = extension.getClass().getAnnotation(ExtensionMapping.class);
                if (java.util.Objects.nonNull(em) && hasText(em.id()) && em.id().equals(extensionId)) {
                    return extension;
                }
            }
        }
        return null;
    }

    /**
     * JDK 字符串判空（替代 Spring StringUtils.hasText）。
     */
    private static boolean hasText(String str) {
        if (java.util.Objects.isNull(str) || !org.springframework.util.StringUtils.hasLength(str)) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
