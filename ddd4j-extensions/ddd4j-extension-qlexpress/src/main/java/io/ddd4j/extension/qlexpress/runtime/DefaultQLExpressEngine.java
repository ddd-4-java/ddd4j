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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 Runner 快照的默认表达式引擎。
 *
 * <p>函数替换或删除时重建 Runner，使正在执行的线程继续使用旧快照，后续执行使用新快照。
 */
public final class DefaultQLExpressEngine implements QLExpressEngine {

    /** \u4fdd\u62a4\u81ea\u5b9a\u4e49\u51fd\u6570\u6ce8\u518c\u548c\u6267\u884c\u5668\u66ff\u6362\u7684\u9501\u3002 */
    private final Object functionLock = new Object();
    /** \u6bcf\u6b21\u6784\u9020\u539f\u751f\u8868\u8fbe\u5f0f\u6267\u884c\u5668\u65f6\u4f7f\u7528\u7684\u521d\u59cb\u5316\u914d\u7f6e\u3002 */
    private final InitOptions initOptions;
    /** \u8c03\u7528\u6267\u884c\u5165\u53e3\u672a\u63d0\u4f9b\u81ea\u5b9a\u4e49\u914d\u7f6e\u65f6\u4f7f\u7528\u7684\u9009\u9879\u3002 */
    private final QLExpressExecutionOptions defaultExecutionOptions;
    /** \u6309\u540d\u79f0\u7ba1\u7406\u5e76\u88c5\u914d\u5230\u539f\u751f\u6267\u884c\u5668\u7684\u8868\u8fbe\u5f0f\u51fd\u6570\u3002 */
    private final Map<String, NamedQLFunction> functions;
    /** \u5f53\u524d\u53d1\u5e03\u7ed9\u8868\u8fbe\u5f0f\u8c03\u7528\u4f7f\u7528\u7684\u539f\u751f\u6267\u884c\u5668\u3002 */
    private volatile Express4Runner runner;

    /**
     * \u6839\u636e\u521d\u59cb\u5316\u9009\u9879\u548c\u51fd\u6570\u5feb\u7167\u521b\u5efa\u8868\u8fbe\u5f0f\u5f15\u64ce\u3002
     * @param initOptions \u5f15\u64ce\u521d\u59cb\u5316\u9009\u9879
     * @param defaultExecutionOptions \u9ed8\u8ba4\u6267\u884c\u9009\u9879
     * @param initialFunctions \u521d\u59cb\u51fd\u6570\u96c6\u5408
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
     * \u68c0\u67e5\u51fd\u6570\u975e\u7a7a\u4e14\u5b58\u5728\u53ef\u6ce8\u518c\u7684\u540d\u79f0\u3002
     * @param function \u5f85\u6ce8\u518c\u7684\u5177\u540d\u51fd\u6570
     * @return \u6821\u9a8c\u901a\u8fc7\u7684\u51fd\u6570\u5bf9\u8c61
     */
    private static NamedQLFunction requireFunction(NamedQLFunction function) {
        NamedQLFunction checked = Objects.requireNonNull(function, "function 不能为空");
        requireFunctionName(checked.name());
        return checked;
    }

    /**
     * \u8981\u6c42\u6ce8\u518c\u540d\u79f0\u542b\u975e\u7a7a\u767d\u5b57\u7b26\u3002
     * @param functionName \u5019\u9009\u7684\u51fd\u6570\u540d\u79f0
     * @throws IllegalArgumentException \u540d\u79f0\u4e3a\u7a7a\u6216\u4ec5\u5305\u542b\u7a7a\u767d
     */
    private static void requireFunctionName(String functionName) {
        if (!StrKit.hasText(functionName)) {
            throw new IllegalArgumentException("functionName 不能为空");
        }
    }

    /**
     * \u8981\u6c42\u8c03\u7528\u65b9\u63d0\u4f9b\u975e\u7a7a\u767d\u7684\u8868\u8fbe\u5f0f\u5185\u5bb9\u3002
     * @param expression \u5f85\u6821\u9a8c\u7684\u8868\u8fbe\u5f0f
     * @throws IllegalArgumentException \u8868\u8fbe\u5f0f\u4e3a\u7a7a\u6216\u4ec5\u5305\u542b\u7a7a\u767d
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
     * @param context 输入上下文
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
     * @param context 输入上下文
     * @param options 执行选项
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
     * @param <T> 结果类型
     * @param expression 表达式
     * @param context 输入上下文
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
     * @param <T> 结果类型
     * @param expression 表达式
     * @param context 输入上下文
     * @param options 执行选项
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
     * @param context 输入上下文
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
     * @param context 输入上下文
     * @param options 执行选项
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
     * \u5e94\u7528\u521d\u59cb\u5316\u9009\u9879\u5e76\u628a\u5f53\u524d\u767b\u8bb0\u7684\u51fd\u6570\u5b89\u88c5\u5230\u65b0\u7684\u539f\u751f\u6267\u884c\u5668\u3002
     * @return \u521d\u59cb\u5316\u5e76\u5b8c\u6210\u51fd\u6570\u6ce8\u518c\u7684\u8868\u8fbe\u5f0f\u6267\u884c\u5668
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
