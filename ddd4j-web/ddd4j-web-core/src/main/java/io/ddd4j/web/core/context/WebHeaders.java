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

    /** \u5e38\u91cf\u503c {@code "Authorization"}\u3002 */
    public static final String AUTHORIZATION = "Authorization";
    /** \u5e38\u91cf\u503c {@code "Idempotency-Key"}\u3002 */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** \u5e38\u91cf\u503c {@code "X-Request-Id"}\u3002 */
    public static final String REQUEST_ID = "X-Request-Id";
    /** \u5e38\u91cf\u503c {@code "X-Trace-Id"}\u3002 */
    public static final String TRACE_ID = "X-Trace-Id";
    /** \u5e38\u91cf\u503c {@code "X-Tenant-Id"}\u3002 */
    public static final String TENANT_ID = "X-Tenant-Id";
    /** \u5e38\u91cf\u503c {@code "X-Forwarded-For"}\u3002 */
    public static final String FORWARDED_FOR = "X-Forwarded-For";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebHeaders</code> \u5b9e\u4f8b\u3002 */
    private WebHeaders() {
    }
}