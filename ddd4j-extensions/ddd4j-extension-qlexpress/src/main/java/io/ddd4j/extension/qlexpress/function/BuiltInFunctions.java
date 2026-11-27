package io.ddd4j.extension.qlexpress.function;

import java.util.List;

/**
 * ddd4j 提供的无状态内置函数集合。
 */
public final class BuiltInFunctions {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>BuiltInFunctions</code> \u5b9e\u4f8b\u3002 */
    private BuiltInFunctions() {
    }

    /**
     * \u83b7\u53d6\u5168\u90e8\u65e0\u72b6\u6001\u5185\u7f6e\u51fd\u6570\u3002
     * @return \u5185\u7f6e\u51fd\u6570\u5217\u8868
     */
    public static List<NamedQLFunction> all() {
        return List.of(
                new ContainsFunction(),
                new StartsWithFunction(),
                new EndsWithFunction(),
                new FormatDateFunction()
        );
    }
}
