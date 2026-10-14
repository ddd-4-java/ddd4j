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
     * 执行表达式。
     *
     * @param expression 表达式
     * @param context 输入上下文
     * @return 表达式结果
     */
    Object execute(String expression, Map<String, Object> context);

    /**
     * 使用指定选项执行表达式。
     *
     * @param expression 表达式
     * @param context 输入上下文
     * @param options 执行选项
     * @return 表达式结果
     */
    Object execute(String expression, Map<String, Object> context, QLExpressExecutionOptions options);

    /**
     * 执行表达式并转换结果类型。
     *
     * @param <T> 结果类型
     * @param expression 表达式
     * @param context 输入上下文
     * @param resultType 目标类型
     * @return 转换后的结果
     */
    <T> T execute(String expression, Map<String, Object> context, Class<T> resultType);

    /**
     * 使用指定选项执行表达式并转换结果类型。
     *
     * @param <T> 结果类型
     * @param expression 表达式
     * @param context 输入上下文
     * @param options 执行选项
     * @param resultType 目标类型
     * @return 转换后的结果
     */
    <T> T execute(String expression, Map<String, Object> context,
                  QLExpressExecutionOptions options, Class<T> resultType);

    /**
     * 执行表达式并将异常封装为结果。
     *
     * @param expression 表达式
     * @param context 输入上下文
     * @return 包含成功或失败信息的执行结果
     */
    QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context);

    /**
     * 使用指定选项执行表达式并封装异常。
     *
     * @param expression 表达式
     * @param context 输入上下文
     * @param options 执行选项
     * @return 包含成功或失败信息的执行结果
     */
    QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context,
                                                   QLExpressExecutionOptions options);

    /**
     * 校验表达式语法。
     *
     * @param expression 表达式
     * @return 语法校验结果
     */
    QLExpressValidationResult validate(String expression);

    /**
     * 获取表达式引用的外部变量。
     *
     * @param expression 表达式
     * @return 外部变量名称
     */
    Set<String> getExternalVariables(String expression);

    /**
     * 获取表达式引用的外部函数。
     *
     * @param expression 表达式
     * @return 外部函数名称
     */
    Set<String> getExternalFunctions(String expression);

    /**
     * 注册自定义函数。
     *
     * @param function 自定义函数
     * @return 注册成功时为 true
     */
    boolean registerFunction(NamedQLFunction function);

    /**
     * 注册或替换自定义函数。
     *
     * @param function 自定义函数
     */
    void registerOrReplaceFunction(NamedQLFunction function);

    /**
     * 移除自定义函数。
     *
     * @param functionName 函数名称
     * @return 移除成功时为 true
     */
    boolean removeFunction(String functionName);

    /**
     * 获取底层 QLExpress Runner。
     *
     * @return 底层 Runner
     */
    Express4Runner unwrap();
}
