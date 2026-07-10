package io.ddd4j.web.webmvc.utils;

import org.springframework.format.Formatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * {@link LocalDateTime} 类型格式化器。
 * <p>用于 Spring MVC 参数绑定中 {@link LocalDateTime} 类型的字符串解析与打印。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LocalDateTimeFormatter implements Formatter<LocalDateTime> {
    /**
     * 日期时间格式化器
     */
    public final DateTimeFormatter FORMATTER;

    /**
     * 构造指定格式的 LocalDateTime 格式化器。
     *
     * @param pattern 日期时间格式模式，如 "yyyy-MM-dd HH:mm:ss"
     */
    public LocalDateTimeFormatter(String pattern) {
        FORMATTER = DateTimeFormatter.ofPattern(pattern, Locale.CHINESE);
    }

    @Override
    public LocalDateTime parse(String text, Locale locale) {
        return LocalDateTime.parse(text, FORMATTER);
    }

    @Override
    public String print(LocalDateTime object, Locale locale) {
        return FORMATTER.format(object);
    }

}
