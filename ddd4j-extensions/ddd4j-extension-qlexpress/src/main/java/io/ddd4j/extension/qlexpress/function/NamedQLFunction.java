package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 带稳定名称的 QLExpress 自定义函数。
 */
public interface NamedQLFunction extends CustomFunction {

    /**
     * \u83b7\u53d6\u51fd\u6570\u6ce8\u518c\u540d\u79f0\u3002
     * @return \u7a33\u5b9a\u7684\u51fd\u6570\u540d\u79f0
     */
    String name();
}
