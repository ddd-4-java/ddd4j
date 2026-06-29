package io.ddd4j.extension.express.infrastructure.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 自定义函数：字符串包含判断
 *
 * <p>硬编码格式的函数，适用于逻辑固定的函数规则。
 * 判断源字符串是否包含目标字符串。
 *
 * <p>使用示例：
 * <pre>
 * contains("hello world", "world") -> true
 * contains("hello world", "java") -> false
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
public class ContainsFunction implements CustomFunction {

    /**
     * 执行函数调用
     *
     * @param qContext   QLExpress上下文
     * @param parameters 函数参数，需要2个参数：source（源字符串）和target（目标字符串）
     * @return 如果源字符串包含目标字符串返回true，否则返回false
     * @throws Throwable 如果参数数量不正确或参数类型错误
     */
    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters == null || parameters.size() < 2) {
            throw new IllegalArgumentException("contains函数需要2个参数：source和target");
        }

        // QLExpress 4.x 中 Parameters 可能直接返回参数值
        // 尝试不同的方式获取参数
        Object sourceObj = getParameterValue(parameters, 0, qContext);
        Object targetObj = getParameterValue(parameters, 1, qContext);

        String source = sourceObj != null ? sourceObj.toString() : null;
        String target = targetObj != null ? targetObj.toString() : null;

        boolean result = source != null && source.contains(target);
        return result;
    }

    /**
     * 获取参数值（兼容不同的 QLExpress 版本）
     */
    private Object getParameterValue(Parameters parameters, int index, QContext qContext) throws Throwable {
        try {
            // 尝试方式1：parameters.get(index).getObject(qContext)
            if (parameters.get(index) != null) {
                Object param = parameters.get(index);
                // 如果参数对象有 getObject 方法
                try {
                    java.lang.reflect.Method getObjectMethod = param.getClass().getMethod("getObject", QContext.class);
                    return getObjectMethod.invoke(param, qContext);
                } catch (NoSuchMethodException e) {
                    // 如果没有 getObject 方法，直接返回参数对象
                    return param;
                }
            }
        } catch (Exception e) {
            // 如果上述方式失败，尝试其他方式
        }

        // 尝试方式2：直接从 parameters 获取
        try {
            java.lang.reflect.Method getMethod = parameters.getClass().getMethod("get", int.class);
            Object param = getMethod.invoke(parameters, index);
            if (param != null) {
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