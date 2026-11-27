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

import java.util.List;

/**
 * ddd4j 提供的无状态内置函数集合。
 */
public final class BuiltInFunctions {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>BuiltInFunctions</code> \u5b9e\u4f8b\u3002 */
    private BuiltInFunctions() {
    }

    /**
     * \u83b7\u53d6\u5168\u90e8\u65e0\u72b6\u6001\u5185\u7f6e\u51fd\u6570\u3002
     * @return \u5185\u7f6e\u51fd\u6570\u5217\u8868
     */
    public static List<NamedQLFunction> all() {
        return List.of(
                new ContainsFunction(),
                new StartsWithFunction(),
                new EndsWithFunction(),
                new FormatDateFunction()
        );
    }
}
