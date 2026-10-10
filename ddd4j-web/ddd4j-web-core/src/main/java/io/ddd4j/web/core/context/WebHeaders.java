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

    /** 授权凭证请求头名称。 */
    public static final String AUTHORIZATION = "Authorization";
    /** 幂等键请求头名称。 */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** 请求标识请求头名称。 */
    public static final String REQUEST_ID = "X-Request-Id";
    /** 链路追踪标识请求头名称。 */
    public static final String TRACE_ID = "X-Trace-Id";
    /** 租户标识请求头名称。 */
    public static final String TENANT_ID = "X-Tenant-Id";
    /** 经过的代理链请求头名称。 */
    public static final String FORWARDED_FOR = "X-Forwarded-For";

    private WebHeaders() {
    }
}
