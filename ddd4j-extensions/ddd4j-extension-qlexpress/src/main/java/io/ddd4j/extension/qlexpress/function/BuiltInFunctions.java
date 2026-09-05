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

    private BuiltInFunctions() {
    }

    public static List<NamedQLFunction> all() {
        return List.of(
                new ContainsFunction(),
                new StartsWithFunction(),
                new EndsWithFunction(),
                new FormatDateFunction()
        );
    }
}
