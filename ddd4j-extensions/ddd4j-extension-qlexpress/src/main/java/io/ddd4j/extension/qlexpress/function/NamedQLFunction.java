package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.function.CustomFunction;

/**
 * 带稳定名称的 QLExpress 自定义函数。
 */
public interface NamedQLFunction extends CustomFunction {

    String name();
}
