package io.ddd4j.core.util;

import java.io.Serializable;
import java.util.function.Function;

/** 可序列化方法引用，用于类型安全查询属性解析。 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
