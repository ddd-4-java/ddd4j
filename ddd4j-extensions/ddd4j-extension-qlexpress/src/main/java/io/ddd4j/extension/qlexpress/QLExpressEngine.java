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

    Object execute(String expression, Map<String, Object> context);

    Object execute(String expression, Map<String, Object> context, QLExpressExecutionOptions options);

    <T> T execute(String expression, Map<String, Object> context, Class<T> resultType);

    <T> T execute(String expression, Map<String, Object> context,
                  QLExpressExecutionOptions options, Class<T> resultType);

    QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context);

    QLExpressExecutionResult<Object> executeSafely(String expression, Map<String, Object> context,
                                                   QLExpressExecutionOptions options);

    QLExpressValidationResult validate(String expression);

    Set<String> getExternalVariables(String expression);

    Set<String> getExternalFunctions(String expression);

    boolean registerFunction(NamedQLFunction function);

    void registerOrReplaceFunction(NamedQLFunction function);

    boolean removeFunction(String functionName);

    Express4Runner unwrap();
}
