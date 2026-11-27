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

    /**
     * \u8fd4\u56de\u8868\u8fbe\u5f0f\u4e2d\u6ce8\u518c\u7684\u51fd\u6570\u540d\u3002
     * @return {@code formatDate}
     */
    @Override
    public String name() {
        return "formatDate";
    }

    /**
     * \u5c06 Date \u6216 java.time \u65e5\u671f\u6309\u6307\u5b9a\u683c\u5f0f\u8f6c\u4e3a\u5b57\u7b26\u4e32\u3002
     * @param qContext \u8868\u8fbe\u5f0f\u4e0a\u4e0b\u6587
     * @param parameters \u65e5\u671f\u5bf9\u8c61\u548c\u683c\u5f0f
     * @return \u683c\u5f0f\u5316\u7ed3\u679c\uff1b\u65e5\u671f\u4e3a null \u65f6\u8fd4\u56de null
     */
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
        if (date instanceof Date legacyDate) {
            Instant instant = legacyDate.toInstant();
            return formatter.format(instant.atZone(ZoneId.systemDefault()));
        }
        if (date instanceof TemporalAccessor temporalAccessor) {
            return formatter.format(temporalAccessor);
        }
        throw new IllegalArgumentException("formatDate 仅支持 Date 和 java.time 日期类型");
    }
}
