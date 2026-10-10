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
package io.ddd4j.extension.qlexpress;

import com.alibaba.qlexpress4.Express4Runner;
import io.ddd4j.extension.qlexpress.function.NamedQLFunction;
import io.ddd4j.extension.qlexpress.model.QLExpressExecutionOptions;
import io.ddd4j.extension.qlexpress.model.QLExpressExecutionResult;
import io.ddd4j.extension.qlexpress.model.QLExpressValidationResult;

import java.util.Map;
import java.util.Set;

/**
 * QLExpress 执行、校验和函数管理的公共抽象。
 */
public interface QLExpressEngine {

    /**
     * 以默认执行选项执行表达式。
     *
     * @param expression 表达式文本
     * @param context    表达式上下文变量
     * @return 表达式求值结果
     */
    Object execute(String expression, Map<String, Object> context);

    /**
     * 以指定执行选项执行表达式。
     *
     * @param expression 表达式文本
     * @param context    表达式上下文变量
     * @param options    单次执行选项（超时、缓存等）
     * @return 表达式求值结果
     */
    Object execute(String expression, Map<String, Object> context, QLExpressExecutionOptions options);

    /**
     * 以默认执行选项执行表达式，并将结果转换为指定类型。
     *
     * @param expression 表达式文本
     * @param context    表达式上下文变量
     * @param resultType 期望的结果类型
     * @param <T>        结果类型
     * @return 转换后的求值结果
     */
    <T> T execute(String expression, Map<String, Object> context, Class<T> resultType);

    /**
     * 以指定执行选项执行表达式，并将结果转换为指定类型。
     *
     * @param expression 表达式文本
     * @param context    表达式上下文变量
     * @param options    单次执行选项（超时、缓存等）
     * @param resultType 期望的结果类型
     * @param <T>        结果类型
     * @return 转换后的求值结果
     */
    <T> T execute(String expression, Map<String, Object> context,
                  QLExpressExecutionOptions options, Class<T> resultType);

    /**
     * 以默认执行选项安全执行表达式，不抛出执行异常。
     *
     * @param expression 表达式文本
     * @param context    表达式上下文变量
     * @return 包含成功标记、结果、错误码与耗时的执行结果
     */
    QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context);

    /**
     * 以指定执行选项安全执行表达式，不抛出执行异常。
     *
     * @param expression 表达式文本
     * @param context    表达式上下文变量
     * @param options    单次执行选项（超时、缓存等）
     * @return 包含成功标记、结果、错误码与耗时的执行结果
     */
    QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context,
                                                   QLExpressExecutionOptions options);

    /**
     * 静态校验表达式是否合法。
     *
     * @param expression 表达式文本
     * @return 校验结果（是否合法及错误提示）
     */
    QLExpressValidationResult validate(String expression);

    /**
     * 分析表达式引用到的外部变量。
     *
     * @param expression 表达式文本
     * @return 外部变量名集合
     */
    Set<String> getExternalVariables(String expression);

    /**
     * 分析表达式引用到的外部函数。
     *
     * @param expression 表达式文本
     * @return 外部函数名集合
     */
    Set<String> getExternalFunctions(String expression);

    /**
     * 注册自定义函数，同名函数已存在时注册失败。
     *
     * @param function 带稳定名称的自定义函数
     * @return 注册成功返回 {@code true}，同名已存在返回 {@code false}
     */
    boolean registerFunction(NamedQLFunction function);

    /**
     * 注册自定义函数，同名函数已存在时直接替换。
     *
     * @param function 带稳定名称的自定义函数
     */
    void registerOrReplaceFunction(NamedQLFunction function);

    /**
     * 移除指定名称的自定义函数。
     *
     * @param functionName 待移除的函数名
     * @return 移除成功返回 {@code true}，函数不存在返回 {@code false}
     */
    boolean removeFunction(String functionName);

    /**
     * 暴露底层执行器，供高级场景直接使用。
     *
     * @return 底层表达式执行器实例
     */
    Express4Runner unwrap();
}
