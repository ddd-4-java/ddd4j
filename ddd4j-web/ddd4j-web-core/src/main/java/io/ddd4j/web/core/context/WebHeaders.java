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

/**
 * ddd4j Web 适配器共同识别的标准与扩展请求头。
 */
public final class WebHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String FORWARDED_FOR = "X-Forwarded-For";

    private WebHeaders() {
    }
}
