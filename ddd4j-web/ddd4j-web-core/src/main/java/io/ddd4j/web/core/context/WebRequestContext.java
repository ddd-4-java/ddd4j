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
package io.ddd4j.web.core.context;

import io.ddd4j.kit.lang.StrKit;

import java.util.Locale;
import java.util.Objects;

/**
 * HTTP 请求在 ddd4j 内部的框架无关表示。
 */
public record WebRequestContext(
        String requestId,
        String traceId,
        String tenantId,
        String authorization,
        Locale locale,
        String clientIp,
        String method,
        String path) {

    public WebRequestContext {
        requestId = StrKit.isBlank(requestId) ? null : requestId;
        traceId = StrKit.isBlank(traceId) ? requestId : traceId;
        locale = Objects.isNull(locale) ? Locale.getDefault() : locale;
        method = StrKit.isBlank(method) ? null : method.toUpperCase(Locale.ROOT);
        path = StrKit.isBlank(path) ? "/" : path;
    }
}
