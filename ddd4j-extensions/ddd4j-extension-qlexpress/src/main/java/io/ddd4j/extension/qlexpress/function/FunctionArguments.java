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

    /**
     * 限制调用方直接创建 <code>FunctionArguments</code> 实例。
     */
    private FunctionArguments() {
    }

    /**
     * 检查函数实参个数是否达到最小要求。
     *
     * @param parameters   本次调用收到的实参
     * @param expected     所需的最少参数数目
     * @param functionName 用于错误诊断的函数名称
     * @throws IllegalArgumentException 实参缺失或数量不足
     */
    static void requireSize(Parameters parameters, int expected, String functionName) {
        if (Objects.isNull(parameters) || parameters.size() < expected) {
            throw new IllegalArgumentException(functionName + " 函数至少需要 " + expected + " 个参数");
        }
    }

    /**
     * 按位置读取引擎传给函数的实际参数值。
     *
     * @param parameters 本次调用收到的实参
     * @param index      参数下标
     * @return 该位置的实参值
     */
    static Object value(Parameters parameters, int index) {
        return parameters.getValue(index);
    }
}
