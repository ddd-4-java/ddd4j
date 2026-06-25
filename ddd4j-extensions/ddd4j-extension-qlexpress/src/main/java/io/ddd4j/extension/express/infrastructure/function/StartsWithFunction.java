package io.ddd4j.extension.express.infrastructure.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 自定义函数：字符串开头判断
 * 
 * <p>硬编码格式的函数，适用于逻辑固定的函数规则。
 * 判断源字符串是否以指定前缀开头。
 * 
 * <p>使用示例：
 * <pre>
 * startsWith("hello world", "hello") -> true
 * startsWith("hello world", "world") -> false
 * </pre>
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public class StartsWithFunction implements CustomFunction {

    /**
     * 执行函数调用
     * 
     * @param qContext QLExpress上下文
     * @param parameters 函数参数，需要2个参数：source（源字符串）和prefix（前缀）
     * @return 如果源字符串以指定前缀开头返回true，否则返回false
     * @throws Throwable 如果参数数量不正确或参数类型错误
     */
    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters == null || parameters.size() < 2) {
            throw new IllegalArgumentException("startsWith函数需要2个参数：source和prefix");
        }
        
        Object sourceObj = getParameterValue(parameters, 0, qContext);
        Object prefixObj = getParameterValue(parameters, 1, qContext);
        
        String source = sourceObj != null ? sourceObj.toString() : null;
        String prefix = prefixObj != null ? prefixObj.toString() : null;

        boolean result = source != null && source.startsWith(prefix);
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

