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

    /**
     * \u6309\u5f53\u524d Spring \u914d\u7f6e\u89e3\u6790\u65e5\u671f\u65f6\u95f4\u6587\u672c\u3002
     * @param text \u5f85\u89e3\u6790\u7684\u65e5\u671f\u6216\u65f6\u95f4\u6587\u672c
     * @param locale \u5f53\u524d\u683c\u5f0f\u5316\u8bf7\u6c42\u7684 Locale
     * @return \u89e3\u6790\u540e\u7684\u672c\u5730\u65e5\u671f\u65f6\u95f4
     */
    @Override
    public LocalDateTime parse(String text, Locale locale) {
        return LocalDateTime.parse(text, FORMATTER);
    }

    /**
     * \u6309\u5f53\u524d Spring \u914d\u7f6e\u683c\u5f0f\u5316\u65e5\u671f\u65f6\u95f4\u3002
     * @param object \u5f85\u5199\u5165\u6216\u683c\u5f0f\u5316\u7684 Java \u5bf9\u8c61
     * @param locale \u5f53\u524d\u683c\u5f0f\u5316\u8bf7\u6c42\u7684 Locale
     * @return \u683c\u5f0f\u5316\u540e\u7684\u65e5\u671f\u65f6\u95f4\u5b57\u7b26\u4e32
     */
    @Override
    public String print(LocalDateTime object, Locale locale) {
        return FORMATTER.format(object);
    }

}
