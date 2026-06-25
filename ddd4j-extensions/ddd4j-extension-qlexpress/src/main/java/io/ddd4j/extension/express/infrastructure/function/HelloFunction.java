package io.ddd4j.extension.express.infrastructure.function;

import com.alibaba.qlexpress4.annotation.QLAlias;
import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 自定义函数：Hello函数示例
 * 
 * <p>硬编码格式的函数，适用于逻辑固定的函数规则。
 * 演示如何从上下文获取参数并返回结果。
 * 
 * <p>使用示例：
 * <pre>
 * hello() -> "hello,{tenant}"
 * </pre>
 * 
 * <p>注意：此函数从上下文的 attachment 中获取 tenant 参数。
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
@QLAlias("hello")
public class HelloFunction implements CustomFunction {
    
    /**
     * 执行函数调用
     * 
     * @param qContext QLExpress上下文，包含attachment等信息
     * @param parameters 函数参数（此函数不需要参数）
     * @return 问候语字符串，格式为 "hello,{tenant}"
     * @throws Throwable 如果获取tenant失败
     */
    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        String tenant = (String) qContext.attachment().get("tenant");
        return "hello," + tenant;
    }
}