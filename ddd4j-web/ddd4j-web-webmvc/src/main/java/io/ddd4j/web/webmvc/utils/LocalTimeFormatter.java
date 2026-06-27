package io.ddd4j.web.utils;

import org.springframework.format.Formatter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class LocalTimeFormatter implements Formatter<LocalTime> {
    public final DateTimeFormatter FORMATTER;

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
