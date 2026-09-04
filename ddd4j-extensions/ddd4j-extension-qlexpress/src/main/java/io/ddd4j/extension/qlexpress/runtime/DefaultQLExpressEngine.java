package io.ddd4j.extension.qlexpress.runtime;

import java.util.Collections;
import java.util.HashSet;
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

    private final Object functionLock = new Object();
    private final InitOptions initOptions;
    private final QLExpressExecutionOptions defaultExecutionOptions;
    private final Map<String, NamedQLFunction> functions;
    private volatile Express4Runner runner;

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

    @Override
    public Object execute(String expression, Map<String, Object> context) {
        return execute(expression, context, defaultExecutionOptions);
    }

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

    @Override
    public <T> T execute(String expression, Map<String, Object> context, Class<T> resultType) {
        return execute(expression, context, defaultExecutionOptions, resultType);
    }

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

    @Override
    public QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context) {
        return executeSafely(expression, context, defaultExecutionOptions);
    }

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

    @Override
    public Set<String> getExternalVariables(String expression) {
        requireExpression(expression);
        return Collections.unmodifiableSet(new HashSet<>(runner.getOutVarNames(expression)));
    }

    @Override
    public Set<String> getExternalFunctions(String expression) {
        requireExpression(expression);
        return Collections.unmodifiableSet(new HashSet<>(runner.getOutFunctions(expression)));
    }

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

    @Override
    public void registerOrReplaceFunction(NamedQLFunction function) {
        NamedQLFunction checked = requireFunction(function);
        synchronized (functionLock) {
            functions.put(checked.name(), checked);
            runner = createRunner();
        }
    }

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

    @Override
    public Express4Runner unwrap() {
        return runner;
    }

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

    private static NamedQLFunction requireFunction(NamedQLFunction function) {
        NamedQLFunction checked = Objects.requireNonNull(function, "function 不能为空");
        requireFunctionName(checked.name());
        return checked;
    }

    private static void requireFunctionName(String functionName) {
        if (!StrKit.hasText(functionName)) {
            throw new IllegalArgumentException("functionName 不能为空");
        }
    }

    private static void requireExpression(String expression) {
        if (!StrKit.hasText(expression)) {
            throw new IllegalArgumentException("expression 不能为空");
        }
    }
}
