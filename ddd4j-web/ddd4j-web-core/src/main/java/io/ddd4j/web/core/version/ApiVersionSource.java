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
package io.ddd4j.web.core.version;

/**
 * API 版本解析结果的来源标识，对应解析优先级的三个层级。
 *
 * @see ApiVersionResolver
 */
public enum ApiVersionSource {

    /** 来自 {@code X-App-Version} 请求头（最高优先级）。 */
    HEADER,

    /** 来自 URL 路径版本段（如 {@code /v2/orders} 中的 {@code v2}）。 */
    PATH,

    /** 来自配置的默认版本（双缺失时的兜底）。 */
    DEFAULT
}
