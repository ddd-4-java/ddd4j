package io.ddd4j.core.util;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的函数式接口（用于 Lambda 属性引用）。
 *
 * <p>类似 MyBatis-Plus 的 {@code SFunction}，通过 {@link java.lang.invoke.SerializedLambda}
 * 解析方法引用对应的属性名，实现编译期类型安全的列引用。
 *
 * <p>使用示例：
 * <pre>{@code
 * SFunction<UserPO, String> getter = UserPO::getName;
 * String property = LambdaKit.resolve(getter); // → "name"
 * }</pre>
 *
 * @param <T> 输入类型（通常是 PO 类）
 * @param <R> 返回类型（字段类型）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
