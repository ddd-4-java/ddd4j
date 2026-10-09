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
 * API 版本的双模式策略：同时用于「非法版本值」与「请求低于最低可用版本」两种场景。
 *
 * @see ApiVersionResolver
 * @see ApiVersionDispatchPolicy
 */
public enum ApiVersionMode {

    /**
     * 回退模式（默认）：解析到非法版本值时回退默认版本（记 debug 日志）；
     * 分发时请求低于最低可用版本则优雅回落到最低可用版本（结果标记 degraded）。
     */
    FALLBACK,

    /**
     * 严格模式：解析到非法版本值时抛出 {@link ApiVersionRejectedException}（4xx 拒绝）；
     * 分发时请求低于最低可用版本直接返回拒绝结果（4xx）。
     */
    STRICT
}
