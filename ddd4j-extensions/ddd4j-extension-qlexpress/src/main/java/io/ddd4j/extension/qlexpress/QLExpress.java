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

/**
 * QLExpress 通用工具门面。
 *
 * <p>该门面只负责创建纯 Java 的表达式引擎，不包含规则仓储、缓存、事件或 Web 能力。
 * 容器集成和规则管理由上层适配模块负责。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class QLExpress {

    private QLExpress() {
    }

    /**
     * 创建使用安全默认值和内置函数的表达式引擎。
     *
     * @return 表达式引擎
     */
    public static QLExpressEngine create() {
        return builder().build();
    }

    /**
     * 创建引擎构建器。
     *
     * @return 引擎构建器
     */
    public static QLExpressEngineBuilder builder() {
        return new QLExpressEngineBuilder();
    }
}
