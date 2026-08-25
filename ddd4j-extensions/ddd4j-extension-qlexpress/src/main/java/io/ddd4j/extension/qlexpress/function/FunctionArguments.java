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
package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.Parameters;

import java.util.Objects;

/**
 * 自定义函数参数读取工具。
 */
final class FunctionArguments {

    private FunctionArguments() {
    }

    static void requireSize(Parameters parameters, int expected, String functionName) {
        if (Objects.isNull(parameters) || parameters.size() < expected) {
            throw new IllegalArgumentException(functionName + " 函数至少需要 " + expected + " 个参数");
        }
    }

    static Object value(Parameters parameters, int index) {
        return parameters.getValue(index);
    }
}
