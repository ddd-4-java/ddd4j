package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.Parameters;

import java.util.Objects;

/**
 * 自定义函数参数读取工具。
 */
final class FunctionArguments {

    private FunctionArguments() {
    }

    static void requireSize(Parameters parameters, int expected, String functionName) {
        if (Objects.isNull(parameters) || parameters.size() < expected) {
            throw new IllegalArgumentException(functionName + " 函数至少需要 " + expected + " 个参数");
        }
    }

    static Object value(Parameters parameters, int index) {
        return parameters.getValue(index);
    }
}
