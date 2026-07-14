package io.ddd4j.extension.qlexpress.function;

import java.util.List;

/**
 * ddd4j 提供的无状态内置函数集合。
 */
public final class BuiltInFunctions {

    private BuiltInFunctions() {
    }

    public static List<NamedQLFunction> all() {
        return List.of(
                new ContainsFunction(),
                new StartsWithFunction(),
                new EndsWithFunction(),
                new FormatDateFunction()
        );
    }
}
