package io.ddd4j.boot.core.util;

import org.springframework.biz.context.NestedMessageSource;
import org.springframework.biz.utils.SpringContextUtils;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 国际化文件获取内容
 */
public class MessagesUtils {

    public static String getMessage(String key) {
        return getMessage(key, null);
    }

    public static String getMessage(String key, String[] args) {
        return SpringContextUtils.getContext().getApplicationContext().getBean(NestedMessageSource.class).getMessage(key, args, LocaleContextHolder.getLocale());
    }

    public static String getMessage(String key, String[] args, String defaultMsg) {
        return SpringContextUtils.getContext().getApplicationContext().getBean(NestedMessageSource.class).getMessage(key, args, defaultMsg, LocaleContextHolder.getLocale());
    }

}
