package io.ddd4j.extension.express.infrastructure.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 自定义函数：字符串结尾判断
 *
 * <p>硬编码格式的函数，适用于逻辑固定的函数规则。
 * 判断源字符串是否以指定后缀结尾。
 *
 * <p>使用示例：
 * <pre>
 * endsWith("hello world", "world") -> true
 * endsWith("hello world", "hello") -> false
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
public class EndsWithFunction implements CustomFunction {

    /**
     * 执行函数调用
     *
     * @param qContext   QLExpress上下文
     * @param parameters 函数参数，需要2个参数：source（源字符串）和suffix（后缀）
     * @return 如果源字符串以指定后缀结尾返回true，否则返回false
     * @throws Throwable 如果参数数量不正确或参数类型错误
     */
    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters == null || parameters.size() < 2) {
            throw new IllegalArgumentException("endsWith函数需要2个参数：source和suffix");
        }

        Object sourceObj = getParameterValue(parameters, 0, qContext);
        Object suffixObj = getParameterValue(parameters, 1, qContext);

        String source = sourceObj != null ? sourceObj.toString() : null;
        String suffix = suffixObj != null ? suffixObj.toString() : null;

        boolean result = source != null && source.endsWith(suffix);
        return result;
    }

    /**
     * 获取参数值（兼容不同的 QLExpress 版本）
     */
    private Object getParameterValue(Parameters parameters, int index, QContext qContext) throws Throwable {
        try {
            if (parameters.get(index) != null) {
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

