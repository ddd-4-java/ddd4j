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

import java.util.Locale;

/**
 * Web 框架采集到的原始请求元数据。
 */
public record WebRequestData(
        String requestId,
        String traceId,
        String tenantId,
        String authorization,
        Locale locale,
        String forwardedFor,
        String realIp,
        String remoteAddress,
        String method,
        String path) {

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getAuthorization() {
            return authorization;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getForwardedFor() {
            return forwardedFor;
        }

    /**
     * 获取值。
     * @return 方法执行结果。
     */
    public Locale getLocale() {
            return locale;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getMethod() {
            return method;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getPath() {
            return path;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getRealIp() {
            return realIp;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getRemoteAddress() {
            return remoteAddress;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getRequestId() {
            return requestId;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getTenantId() {
            return tenantId;
        }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getTraceId() {
            return traceId;
        }
}
