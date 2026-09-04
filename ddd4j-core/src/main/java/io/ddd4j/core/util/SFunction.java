/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * SFunction<User, String> getter = User::getName;
 * String property = LambdaKit.resolve(getter); // → "name"
 * }</pre>
 *
 * @param <T> 输入类型（Query 中为领域模型类型）
 * @param <R> 返回类型（字段类型）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
