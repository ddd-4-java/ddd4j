package io.ddd4j.web.webmvc.utils;

import org.springframework.format.Formatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class LocalDateTimeFormatter implements Formatter<LocalDateTime> {
    public final DateTimeFormatter FORMATTER;

    public LocalDateTimeFormatter(String pattern) {
        FORMATTER = DateTimeFormatter.ofPattern(pattern, Locale.CHINESE);
    }

    public LocalDateTime parse(String text, Locale locale) {
        return LocalDateTime.parse(text, FORMATTER);
    }

    public String print(LocalDateTime object, Locale locale) {
        return FORMATTER.format(object);
    }

}
