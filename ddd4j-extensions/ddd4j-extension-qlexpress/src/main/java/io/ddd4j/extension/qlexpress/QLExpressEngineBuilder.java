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

    /** \u6784\u9020\u5f15\u64ce\u524d\u6309\u63d2\u5165\u987a\u5e8f\u767b\u8bb0\u7684\u5177\u540d\u51fd\u6570\u3002 */
    private final Map<String, NamedQLFunction> functions = new LinkedHashMap<>();
    /** \u662f\u5426\u6ce8\u518c\u6846\u67b6\u63d0\u4f9b\u7684\u5185\u7f6e\u8868\u8fbe\u5f0f\u51fd\u6570\u3002 */
    private boolean builtInFunctions = true;
    /** \u521d\u59cb\u5316\u8868\u8fbe\u5f0f\u6267\u884c\u5668\u65f6\u662f\u5426\u5141\u8bb8\u8bbf\u95ee\u975e\u516c\u5f00\u6210\u5458\u3002 */
    private boolean allowPrivateAccess;
    /** \u521d\u59cb\u5316\u6267\u884c\u5668\u65f6\u662f\u5426\u5f00\u542f\u8868\u8fbe\u5f0f\u8ddf\u8e2a\u3002 */
    private boolean traceExpression;
    /** \u63a7\u5236\u8868\u8fbe\u5f0f\u53ef\u8bbf\u95ee\u5bf9\u8c61\u548c\u884c\u4e3a\u7684\u5f15\u64ce\u5b89\u5168\u7b56\u7565\u3002 */
    private QLSecurityStrategy securityStrategy = QLSecurityStrategy.isolation();
    /** \u8c03\u7528\u65b9\u672a\u63d0\u4f9b\u6267\u884c\u9009\u9879\u65f6\u91c7\u7528\u7684\u9ed8\u8ba4\u914d\u7f6e\u3002 */
    private QLExpressExecutionOptions defaultExecutionOptions = QLExpressExecutionOptions.defaults();

    /**
     * \u542f\u7528\u6216\u7981\u7528\u5185\u7f6e\u51fd\u6570\u3002
     * @param enabled \u662f\u5426\u542f\u7528
     * @return \u5f53\u524d\u6784\u5efa\u5668
     */
    public QLExpressEngineBuilder builtInFunctions(boolean enabled) {
        this.builtInFunctions = enabled;
        return this;
    }

    /**
     * \u914d\u7f6e\u79c1\u6709\u6210\u5458\u8bbf\u95ee\u3002
     * @param allowed \u662f\u5426\u5141\u8bb8
     * @return \u5f53\u524d\u6784\u5efa\u5668
     */
    public QLExpressEngineBuilder allowPrivateAccess(boolean allowed) {
        this.allowPrivateAccess = allowed;
        return this;
    }

    /**
     * \u914d\u7f6e\u8868\u8fbe\u5f0f\u8ffd\u8e2a\u3002
     * @param enabled \u662f\u5426\u542f\u7528
     * @return \u5f53\u524d\u6784\u5efa\u5668
     */
    public QLExpressEngineBuilder traceExpression(boolean enabled) {
        this.traceExpression = enabled;
        return this;
    }

    /**
     * \u914d\u7f6e\u6267\u884c\u5b89\u5168\u7b56\u7565\u3002
     * @param securityStrategy \u5b89\u5168\u7b56\u7565
     * @return \u5f53\u524d\u6784\u5efa\u5668
     */
    public QLExpressEngineBuilder securityStrategy(QLSecurityStrategy securityStrategy) {
        this.securityStrategy = Objects.requireNonNull(securityStrategy, "securityStrategy 不能为空");
        return this;
    }

    /**
     * \u914d\u7f6e\u9ed8\u8ba4\u6267\u884c\u9009\u9879\u3002
     * @param options \u6267\u884c\u9009\u9879
     * @return \u5f53\u524d\u6784\u5efa\u5668
     */
    public QLExpressEngineBuilder defaultExecutionOptions(QLExpressExecutionOptions options) {
        this.defaultExecutionOptions = Objects.requireNonNull(options, "options 不能为空");
        return this;
    }

    /**
     * \u6ce8\u518c\u6784\u5efa\u65f6\u53ef\u7528\u7684\u81ea\u5b9a\u4e49\u51fd\u6570\u3002
     * @param function \u81ea\u5b9a\u4e49\u51fd\u6570
     * @return \u5f53\u524d\u6784\u5efa\u5668
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
     * \u521b\u5efa QLExpress \u5f15\u64ce\u3002
     * @return QLExpress \u5f15\u64ce
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
