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

    /**
     * builtInFunctions() 方法。
     * @param enabled 是否启用。
     * @return 当前对象自身，支持链式调用。
     */
    public QLExpressEngineBuilder builtInFunctions(boolean enabled) {
        this.builtInFunctions = enabled;
        return this;
    }

    /**
     * allowPrivateAccess() 方法。
     * @param allowed allowed 参数。
     * @return 当前对象自身，支持链式调用。
     */
    public QLExpressEngineBuilder allowPrivateAccess(boolean allowed) {
        this.allowPrivateAccess = allowed;
        return this;
    }

    /**
     * traceExpression() 方法。
     * @param enabled 是否启用。
     * @return 当前对象自身，支持链式调用。
     */
    public QLExpressEngineBuilder traceExpression(boolean enabled) {
        this.traceExpression = enabled;
        return this;
    }

    /**
     * securityStrategy() 方法。
     * @param securityStrategy securityStrategy 参数。
     * @return 当前对象自身，支持链式调用。
     */
    public QLExpressEngineBuilder securityStrategy(QLSecurityStrategy securityStrategy) {
        this.securityStrategy = Objects.requireNonNull(securityStrategy, "securityStrategy 不能为空");
        return this;
    }

    /**
     * defaultExecutionOptions() 方法。
     * @param options options 参数。
     * @return 当前对象自身，支持链式调用。
     */
    public QLExpressEngineBuilder defaultExecutionOptions(QLExpressExecutionOptions options) {
        this.defaultExecutionOptions = Objects.requireNonNull(options, "options 不能为空");
        return this;
    }

    /**
     * function() 方法。
     * @param function 处理函数。
     * @return 当前对象自身，支持链式调用。
     */
    public QLExpressEngineBuilder function(NamedQLFunction function) {
        NamedQLFunction checked = Objects.requireNonNull(function, "function 不能为空");
        if (!StrKit.hasText(checked.name())) {
            throw new IllegalArgumentException("function.name 不能为空");
        }
        functions.put(checked.name(), checked);
        return this;
    }

    /**
     * 构建对象。
     * @return 方法执行结果。
     */
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
