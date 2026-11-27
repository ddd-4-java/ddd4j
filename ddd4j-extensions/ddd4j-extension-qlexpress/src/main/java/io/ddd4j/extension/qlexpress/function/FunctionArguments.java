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

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>FunctionArguments</code> \u5b9e\u4f8b\u3002 */
    private FunctionArguments() {
    }

    /**
     * \u68c0\u67e5\u51fd\u6570\u5b9e\u53c2\u4e2a\u6570\u662f\u5426\u8fbe\u5230\u6700\u5c0f\u8981\u6c42\u3002
     * @param parameters \u672c\u6b21\u8c03\u7528\u6536\u5230\u7684\u5b9e\u53c2
     * @param expected \u6240\u9700\u7684\u6700\u5c11\u53c2\u6570\u6570\u76ee
     * @param functionName \u7528\u4e8e\u9519\u8bef\u8bca\u65ad\u7684\u51fd\u6570\u540d\u79f0
     * @throws IllegalArgumentException \u5b9e\u53c2\u7f3a\u5931\u6216\u6570\u91cf\u4e0d\u8db3
     */
    static void requireSize(Parameters parameters, int expected, String functionName) {
        if (Objects.isNull(parameters) || parameters.size() < expected) {
            throw new IllegalArgumentException(functionName + " 函数至少需要 " + expected + " 个参数");
        }
    }

    /**
     * \u6309\u4f4d\u7f6e\u8bfb\u53d6\u5f15\u64ce\u4f20\u7ed9\u51fd\u6570\u7684\u5b9e\u9645\u53c2\u6570\u503c\u3002
     * @param parameters \u672c\u6b21\u8c03\u7528\u6536\u5230\u7684\u5b9e\u53c2
     * @param index \u53c2\u6570\u4e0b\u6807
     * @return \u8be5\u4f4d\u7f6e\u7684\u5b9e\u53c2\u503c
     */
    static Object value(Parameters parameters, int index) {
        return parameters.getValue(index);
    }
}
