package io.ddd4j.web.webmvc.utils;

import org.springframework.format.Formatter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * {@link LocalTime} 类型格式化器。
 * <p>用于 Spring MVC 参数绑定中 {@link LocalTime} 类型的字符串解析与打印。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LocalTimeFormatter implements Formatter<LocalTime> {
    /** 时间格式化器 */
    public final DateTimeFormatter FORMATTER;

    /**
     * 构造指定格式的 LocalTime 格式化器。
     *
     * @param pattern 时间格式模式，如 "HH:mm:ss"
     */
    public LocalTimeFormatter(String pattern) {
        FORMATTER = DateTimeFormatter.ofPattern(pattern, Locale.CHINESE);
    }

    @Override
    public LocalTime parse(String text, Locale locale) {
        return LocalTime.parse(text, FORMATTER);
    }

    @Override
    public String print(LocalTime object, Locale locale) {
        return FORMATTER.format(object);
    }

}
