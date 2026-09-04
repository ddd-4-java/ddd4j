package io.ddd4j.extension.qlexpress;

import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import io.ddd4j.extension.qlexpress.function.BuiltInFunctions;
import io.ddd4j.extension.qlexpress.function.NamedQLFunction;
import io.ddd4j.extension.qlexpress.model.QLExpressExecutionOptions;
import io.ddd4j.extension.qlexpress.runtime.DefaultQLExpressEngine;
import io.ddd4j.kit.lang.StrKit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link QLExpressEngine} 构建器。
 */
public final class QLExpressEngineBuilder {

    private boolean builtInFunctions = true;
    private boolean allowPrivateAccess;
    private boolean traceExpression;
    private QLSecurityStrategy securityStrategy = QLSecurityStrategy.isolation();
    private QLExpressExecutionOptions defaultExecutionOptions = QLExpressExecutionOptions.defaults();
    private final Map<String, NamedQLFunction> functions = new LinkedHashMap<>();

    public QLExpressEngineBuilder builtInFunctions(boolean enabled) {
        this.builtInFunctions = enabled;
        return this;
    }

    public QLExpressEngineBuilder allowPrivateAccess(boolean allowed) {
        this.allowPrivateAccess = allowed;
        return this;
    }

    public QLExpressEngineBuilder traceExpression(boolean enabled) {
        this.traceExpression = enabled;
        return this;
    }

    public QLExpressEngineBuilder securityStrategy(QLSecurityStrategy securityStrategy) {
        this.securityStrategy = Objects.requireNonNull(securityStrategy, "securityStrategy 不能为空");
        return this;
    }

    public QLExpressEngineBuilder defaultExecutionOptions(QLExpressExecutionOptions options) {
        this.defaultExecutionOptions = Objects.requireNonNull(options, "options 不能为空");
        return this;
    }

    public QLExpressEngineBuilder function(NamedQLFunction function) {
        NamedQLFunction checked = Objects.requireNonNull(function, "function 不能为空");
        if (!StrKit.hasText(checked.name())) {
            throw new IllegalArgumentException("function.name 不能为空");
        }
        functions.put(checked.name(), checked);
        return this;
    }

    public QLExpressEngine build() {
        Map<String, NamedQLFunction> initialFunctions = new LinkedHashMap<>();
        if (builtInFunctions) {
            BuiltInFunctions.all().forEach(function -> initialFunctions.put(function.name(), function));
        }
        initialFunctions.putAll(functions);

        InitOptions initOptions = InitOptions.builder()
                .securityStrategy(securityStrategy)
                .allowPrivateAccess(allowPrivateAccess)
                .traceExpression(traceExpression)
                .build();
        return new DefaultQLExpressEngine(initOptions, defaultExecutionOptions, initialFunctions);
    }
}
