package io.ddd4j.extension.express.infrastructure.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

/**
 * 自定义函数：日期格式化
 *
 * <p>硬编码格式的函数，适用于逻辑固定的函数规则。
 * 将日期对象格式化为指定格式的字符串。
 *
 * <p>使用示例：
 * <pre>
 * formatDate(date, "yyyy-MM-dd HH:mm:ss")
 * formatDate(localDateTime, "yyyy/MM/dd")
 * </pre>
 *
 * <p>支持的日期类型：
 * <ul>
 *   <li>java.util.Date</li>
 *   <li>java.time.LocalDateTime</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
public class FormatDateFunction implements CustomFunction {

    /**
     * 执行函数调用
     *
     * @param qContext   QLExpress上下文
     * @param parameters 函数参数，需要2个参数：date（日期对象）和pattern（格式化模式）
     * @return 格式化后的日期字符串
     * @throws Throwable 如果参数数量不正确、参数类型错误或格式化失败
     */
    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (Objects.isNull(parameters) || parameters.size() < 2) {
            throw new IllegalArgumentException("formatDate函数需要2个参数：date和pattern");
        }

        Object dateObj = getParameterValue(parameters, 0, qContext);
        Object patternObj = getParameterValue(parameters, 1, qContext);
        String pattern = Objects.nonNull(patternObj) ? patternObj.toString() : null;

        if (Objects.isNull(dateObj)) {
            return null;
        }

        if (dateObj instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            return sdf.format((Date) dateObj);
        } else if (dateObj instanceof LocalDateTime) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return ((LocalDateTime) dateObj).format(formatter);
        } else {
            throw new IllegalArgumentException("formatDate函数第一个参数必须是Date或LocalDateTime类型");
        }
    }

    /**
     * 获取参数值（兼容不同的 QLExpress 版本）
     */
    private Object getParameterValue(Parameters parameters, int index, QContext qContext) throws Throwable {
        try {
            if (Objects.nonNull(parameters.get(index))) {
                Object param = parameters.get(index);
                try {
                    java.lang.reflect.Method getObjectMethod = param.getClass().getMethod("getObject", QContext.class);
                    return getObjectMethod.invoke(param, qContext);
                } catch (NoSuchMethodException e) {
                    return param;
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        throw new IllegalArgumentException("无法获取参数值，索引: " + index);
    }
}