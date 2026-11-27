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
package io.ddd4j.extension.qlexpress.runtime;

import com.alibaba.qlexpress4.CheckOptions;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import io.ddd4j.extension.qlexpress.QLExpressEngine;
import io.ddd4j.extension.qlexpress.exception.QLExpressExecutionException;
import io.ddd4j.extension.qlexpress.function.NamedQLFunction;
import io.ddd4j.extension.qlexpress.model.QLExpressExecutionOptions;
import io.ddd4j.extension.qlexpress.model.QLExpressExecutionResult;
import io.ddd4j.extension.qlexpress.model.QLExpressValidationResult;
import io.ddd4j.kit.lang.StrKit;

import java.util.*;

/**
 * 基于 Runner 快照的默认表达式引擎。
 *
 * <p>函数替换或删除时重建 Runner，使正在执行的线程继续使用旧快照，后续执行使用新快照。
 */
public final class DefaultQLExpressEngine implements QLExpressEngine {

    /**
     * 保护自定义函数注册和执行器替换的锁。
     */
    private final Object functionLock = new Object();
    /**
     * 每次构造原生表达式执行器时使用的初始化配置。
     */
    private final InitOptions initOptions;
    /**
     * 调用执行入口未提供自定义配置时使用的选项。
     */
    private final QLExpressExecutionOptions defaultExecutionOptions;
    /**
     * 按名称管理并装配到原生执行器的表达式函数。
     */
    private final Map<String, NamedQLFunction> functions;
    /**
     * 当前发布给表达式调用使用的原生执行器。
     */
    private volatile Express4Runner runner;

    /**
     * 根据初始化选项和函数快照创建表达式引擎。
     *
     * @param initOptions             引擎初始化选项
     * @param defaultExecutionOptions 默认执行选项
     * @param initialFunctions        初始函数集合
     */
    public DefaultQLExpressEngine(InitOptions initOptions,
                                  QLExpressExecutionOptions defaultExecutionOptions,
                                  Map<String, NamedQLFunction> initialFunctions) {
        this.initOptions = Objects.requireNonNull(initOptions, "initOptions 不能为空");
        this.defaultExecutionOptions = Objects.requireNonNull(
                defaultExecutionOptions, "defaultExecutionOptions 不能为空");
        this.functions = new LinkedHashMap<>();
        if (Objects.nonNull(initialFunctions)) {
            this.functions.putAll(initialFunctions);
        }
        this.runner = createRunner();
    }

    /**
     * 检查函数非空且存在可注册的名称。
     *
     * @param function 待注册的具名函数
     * @return 校验通过的函数对象
     */
    private static NamedQLFunction requireFunction(NamedQLFunction function) {
        NamedQLFunction checked = Objects.requireNonNull(function, "function 不能为空");
        requireFunctionName(checked.name());
        return checked;
    }

    /**
     * 要求注册名称含非空白字符。
     *
     * @param functionName 候选的函数名称
     * @throws IllegalArgumentException 名称为空或仅包含空白
     */
    private static void requireFunctionName(String functionName) {
        if (!StrKit.hasText(functionName)) {
            throw new IllegalArgumentException("functionName 不能为空");
        }
    }

    /**
     * 要求调用方提供非空白的表达式内容。
     *
     * @param expression 待校验的表达式
     * @throws IllegalArgumentException 表达式为空或仅包含空白
     */
    private static void requireExpression(String expression) {
        if (!StrKit.hasText(expression)) {
            throw new IllegalArgumentException("expression 不能为空");
        }
    }

    /**
     * 用当前处理器执行传入命令或操作。
     *
     * <p>此实现调用 <code>execute(expression, context, defaultExecutionOptions)</code>。</p>
     *
     * @param expression 表达式
     * @param context    输入上下文
     * @return 实际返回<code>execute(expression, context, defaultExecutionOptions)</code>
     */
    @Override
    public Object execute(String expression, Map<String, Object> context) {
        return execute(expression, context, defaultExecutionOptions);
    }

    /**
     * 用当前处理器执行传入命令或操作。
     *
     * <p>此实现调用 <code>requireExpression(expression)</code>。</p>
     *
     * @param expression 表达式
     * @param context    输入上下文
     * @param options    执行选项
     * @return 实际返回<code>result.getResult()</code>
     */
    @Override
    public Object execute(String expression, Map<String, Object> context, QLExpressExecutionOptions options) {
        requireExpression(expression);
        QLExpressExecutionOptions checkedOptions = Objects.requireNonNull(options, "options 不能为空");
        Map<String, Object> safeContext = Objects.isNull(context) ? Collections.emptyMap() : context;
        try {
            QLResult result = runner.execute(expression, safeContext, checkedOptions.toNativeOptions());
            return result.getResult();
        } catch (Exception exception) {
            throw new QLExpressExecutionException(expression, exception);
        }
    }

    /**
     * 用当前处理器执行传入命令或操作。
     *
     * <p>此实现调用 <code>execute(expression, context, defaultExecutionOptions, resultType)</code>。</p>
     *
     * @param <T>        结果类型
     * @param expression 表达式
     * @param context    输入上下文
     * @param resultType 目标类型
     * @return 实际返回<code>execute(expression, context, defaultExecutionOptions, resultType)</code>
     */
    @Override
    public <T> T execute(String expression, Map<String, Object> context, Class<T> resultType) {
        return execute(expression, context, defaultExecutionOptions, resultType);
    }

    /**
     * 用当前处理器执行传入命令或操作。
     *
     * <p>此实现调用 <code>execute(expression, context, options)</code>。</p>
     *
     * @param <T>        结果类型
     * @param expression 表达式
     * @param context    输入上下文
     * @param options    执行选项
     * @param resultType 目标类型
     * @return 根据执行路径返回<code>null</code>、<code>checkedResultType.cast(value)</code>
     */
    @Override
    public <T> T execute(String expression, Map<String, Object> context,
                         QLExpressExecutionOptions options, Class<T> resultType) {
        Class<T> checkedResultType = Objects.requireNonNull(resultType, "resultType 不能为空");
        Object value = execute(expression, context, options);
        if (Objects.isNull(value)) {
            return null;
        }
        if (!checkedResultType.isInstance(value)) {
            throw new ClassCastException("表达式结果类型为 " + value.getClass().getName()
                    + "，不能转换为 " + checkedResultType.getName());
        }
        return checkedResultType.cast(value);
    }

    /**
     * 执行表达式并将运行或解析失败封装为执行结果。
     *
     * <p>此实现调用 <code>executeSafely(expression, context, defaultExecutionOptions)</code>。</p>
     *
     * @param expression 表达式
     * @param context    输入上下文
     * @return 实际返回<code>executeSafely(expression, context, defaultExecutionOptions)</code>
     */
    @Override
    public QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context) {
        return executeSafely(expression, context, defaultExecutionOptions);
    }

    /**
     * 执行表达式并将运行或解析失败封装为执行结果。
     *
     * <p>此实现调用 <code>System.nanoTime()</code>。</p>
     *
     * @param expression 表达式
     * @param context    输入上下文
     * @param options    执行选项
     * @return 实际返回<code>QLExpressExecutionResult.success(value, System.nanoTime() - startedAt)</code>
     */
    @Override
    public QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context,
                                                          QLExpressExecutionOptions options) {
        long startedAt = System.nanoTime();
        try {
            Object value = execute(expression, context, options);
            return QLExpressExecutionResult.success(value, System.nanoTime() - startedAt);
        } catch (RuntimeException exception) {
            return QLExpressExecutionResult.failure(
                    exception.getClass().getSimpleName(), exception.getMessage(), System.nanoTime() - startedAt);
        }
    }

    /**
     * 校验当前调用提交的数据。
     *
     * <p>此实现调用 <code>StrKit.hasText(expression)</code>。</p>
     *
     * @param expression 表达式
     * @return 根据执行路径返回<code>QLExpressValidationResult.invalid("\u8868\u8fbe\u5f0f\u4e0d\u80fd\u4e3a\u7a7a")</code>、<code>QLExpressValidationResult.success()</code>、<code>QLExpressValidationResult.invalid(exception.getMessage())</code>
     */
    @Override
    public QLExpressValidationResult validate(String expression) {
        if (!StrKit.hasText(expression)) {
            return QLExpressValidationResult.invalid("表达式不能为空");
        }
        try {
            runner.check(expression, CheckOptions.DEFAULT_OPTIONS);
            return QLExpressValidationResult.success();
        } catch (Exception exception) {
            return QLExpressValidationResult.invalid(exception.getMessage());
        }
    }

    /**
     * 返回表达式引擎可读取的外部变量。
     *
     * <p>此实现调用 <code>requireExpression(expression)</code>。</p>
     *
     * @param expression 表达式
     * @return 实际返回<code>Collections.unmodifiableSet(new HashSet&lt;&gt;(runner.getOutVarNames(expression)))</code>
     */
    @Override
    public Set<String> getExternalVariables(String expression) {
        requireExpression(expression);
        return Collections.unmodifiableSet(new HashSet<>(runner.getOutVarNames(expression)));
    }

    /**
     * 返回表达式引擎已登记的外部函数。
     *
     * <p>此实现调用 <code>requireExpression(expression)</code>。</p>
     *
     * @param expression 表达式
     * @return 实际返回<code>Collections.unmodifiableSet(new HashSet&lt;&gt;(runner.getOutFunctions(expression)))</code>
     */
    @Override
    public Set<String> getExternalFunctions(String expression) {
        requireExpression(expression);
        return Collections.unmodifiableSet(new HashSet<>(runner.getOutFunctions(expression)));
    }

    /**
     * 向表达式引擎注册自定义函数。
     *
     * <p>此实现调用 <code>requireFunction(function)</code>。</p>
     *
     * @param function 自定义函数
     * @return 根据执行路径返回<code>false</code>、<code>true</code>
     */
    @Override
    public boolean registerFunction(NamedQLFunction function) {
        NamedQLFunction checked = requireFunction(function);
        synchronized (functionLock) {
            if (functions.containsKey(checked.name())) {
                return false;
            }
            functions.put(checked.name(), checked);
            runner = createRunner();
            return true;
        }
    }

    /**
     * 写入表达式函数注册项并允许替换旧实现。
     *
     * <p>此实现调用 <code>requireFunction(function)</code>。</p>
     *
     * @param function 自定义函数
     */
    @Override
    public void registerOrReplaceFunction(NamedQLFunction function) {
        NamedQLFunction checked = requireFunction(function);
        synchronized (functionLock) {
            functions.put(checked.name(), checked);
            runner = createRunner();
        }
    }

    /**
     * 从表达式引擎移除指定函数。
     *
     * <p>此实现调用 <code>requireFunctionName(functionName)</code>。</p>
     *
     * @param functionName 函数名称
     * @return 根据执行路径返回<code>false</code>、<code>true</code>
     */
    @Override
    public boolean removeFunction(String functionName) {
        requireFunctionName(functionName);
        synchronized (functionLock) {
            if (Objects.isNull(functions.remove(functionName))) {
                return false;
            }
            runner = createRunner();
            return true;
        }
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * @return 实际返回<code>runner</code>，供调用方取得当前值
     */
    @Override
    public Express4Runner unwrap() {
        return runner;
    }

    /**
     * 应用初始化选项并把当前登记的函数安装到新的原生执行器。
     *
     * @return 初始化并完成函数注册的表达式执行器
     */
    private Express4Runner createRunner() {
        Express4Runner newRunner = new Express4Runner(initOptions);
        for (Map.Entry<String, NamedQLFunction> entry : functions.entrySet()) {
            CustomFunction function = entry.getValue();
            if (!newRunner.addFunction(entry.getKey(), function)) {
                throw new IllegalStateException("QLExpress 函数重复: " + entry.getKey());
            }
        }
        return newRunner;
    }
}
