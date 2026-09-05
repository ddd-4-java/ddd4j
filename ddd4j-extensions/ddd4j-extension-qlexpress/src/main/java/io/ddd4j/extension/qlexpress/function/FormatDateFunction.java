package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import io.ddd4j.kit.lang.StrKit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Objects;

/**
 * 格式化 Java 日期时间对象。
 */
public final class FormatDateFunction implements NamedQLFunction {

    @Override
    public String name() {
        return "formatDate";
    }

    @Override
    public Object call(QContext qContext, Parameters parameters) {
        FunctionArguments.requireSize(parameters, 2, name());
        Object date = FunctionArguments.value(parameters, 0);
        Object patternValue = FunctionArguments.value(parameters, 1);
        if (Objects.isNull(date)) {
            return null;
        }
        if (Objects.isNull(patternValue) || !StrKit.hasText(patternValue.toString())) {
            throw new IllegalArgumentException("formatDate 格式不能为空");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(patternValue.toString());
        if (date instanceof Date) {
            Instant instant = ((Date) date).toInstant();
            return formatter.format(instant.atZone(ZoneId.systemDefault()));
        }
        if (date instanceof TemporalAccessor) {
            return formatter.format((TemporalAccessor) date);
        }
        throw new IllegalArgumentException("formatDate 仅支持 Date 和 java.time 日期类型");
    }
}
