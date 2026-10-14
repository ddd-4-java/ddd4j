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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.version.ApiVersion;

import java.util.Locale;
import java.util.Objects;

/**
 * HTTP 请求在 ddd4j 内部的框架无关表示。
 */
public final class WebRequestContext {

    private static final long serialVersionUID = 0L;

    private final String requestId;

    private final String traceId;

    private final String tenantId;

    private final String authorization;

    private final Locale locale;

    private final String clientIp;

    private final String method;

    private final String path;

    private final ApiVersion apiVersion;

    /**
     * 创建已规范化的请求上下文。
     *
     * @param requestId 请求标识
     * @param traceId 跟踪标识
     * @param tenantId 租户标识
     * @param authorization Authorization 请求头
     * @param locale 区域设置
     * @param clientIp 客户端 IP 地址
     * @param method HTTP 方法
     * @param path 请求路径
     */
    public WebRequestContext(String requestId, String traceId, String tenantId, String authorization, Locale locale, String clientIp, String method, String path) {
        this(requestId, traceId, tenantId, authorization, locale, clientIp, method, path, null);
    }

    /**
     * 创建包含 API 版本的请求上下文。
     *
     * @param requestId 请求标识
     * @param traceId 跟踪标识
     * @param tenantId 租户标识
     * @param authorization Authorization 请求头
     * @param locale 区域设置
     * @param clientIp 客户端 IP 地址
     * @param method HTTP 方法
     * @param path 请求路径
     * @param apiVersion API 版本，可为空
     */
    @JsonCreator
    public WebRequestContext(@JsonProperty("requestId") String requestId, @JsonProperty("traceId") String traceId, @JsonProperty("tenantId") String tenantId, @JsonProperty("authorization") String authorization, @JsonProperty("locale") Locale locale, @JsonProperty("clientIp") String clientIp, @JsonProperty("method") String method, @JsonProperty("path") String path, @JsonProperty("apiVersion") ApiVersion apiVersion) {
        requestId = StrKit.isBlank(requestId) ? null : requestId;
        traceId = StrKit.isBlank(traceId) ? requestId : traceId;
        locale = Objects.isNull(locale) ? Locale.getDefault() : locale;
        method = StrKit.isBlank(method) ? null : method.toUpperCase(Locale.ROOT);
        path = StrKit.isBlank(path) ? "/" : path;
        this.requestId = requestId;
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.authorization = authorization;
        this.locale = locale;
        this.clientIp = clientIp;
        this.method = method;
        this.path = path;
        this.apiVersion = apiVersion;
    }

    /**
     * 返回Authorization 请求头。
     *
     * @return Authorization 请求头
     */

    public String getAuthorization() {
        return authorization;
    }

    /**
     * 返回客户端 IP 地址。
     *
     * @return 客户端 IP 地址
     */

    public String getClientIp() {
        return clientIp;
    }

    /**
     * 返回区域设置。
     *
     * @return 区域设置
     */

    public Locale getLocale() {
        return locale;
    }

    /**
     * 返回HTTP 方法。
     *
     * @return HTTP 方法
     */

    public String getMethod() {
        return method;
    }

    /**
     * 返回请求路径。
     *
     * @return 请求路径
     */

    public String getPath() {
        return path;
    }

    /**
     * 返回请求标识。
     *
     * @return 请求标识
     */

    public String getRequestId() {
        return requestId;
    }

    /**
     * 返回租户标识。
     *
     * @return 租户标识
     */

    public String getTenantId() {
        return tenantId;
    }

    /**
     * 返回跟踪标识。
     *
     * @return 跟踪标识
     */

    public String getTraceId() {
        return traceId;
    }

    /**
     * 返回请求标识。
     *
     * @return 请求标识
     */
    @JsonProperty("requestId")
    public String requestId() {
        return requestId;
    }

    /**
     * 返回跟踪标识。
     *
     * @return 跟踪标识
     */
    @JsonProperty("traceId")
    public String traceId() {
        return traceId;
    }

    /**
     * 返回租户标识。
     *
     * @return 租户标识
     */
    @JsonProperty("tenantId")
    public String tenantId() {
        return tenantId;
    }

    /**
     * 返回Authorization 请求头。
     *
     * @return Authorization 请求头
     */
    @JsonProperty("authorization")
    public String authorization() {
        return authorization;
    }

    /**
     * 返回区域设置。
     *
     * @return 区域设置
     */
    @JsonProperty("locale")
    public Locale locale() {
        return locale;
    }

    /**
     * 返回客户端 IP 地址。
     *
     * @return 客户端 IP 地址
     */
    @JsonProperty("clientIp")
    public String clientIp() {
        return clientIp;
    }

    /**
     * 返回HTTP 方法。
     *
     * @return HTTP 方法
     */
    @JsonProperty("method")
    public String method() {
        return method;
    }

    /**
     * 返回请求路径。
     *
     * @return 请求路径
     */
    @JsonProperty("path")
    public String path() {
        return path;
    }

    /** @return API 版本，未指定时为空 */
    @JsonProperty("apiVersion")
    public ApiVersion apiVersion() {
        return apiVersion;
    }

    /** @return API 版本，未指定时为空 */
    public ApiVersion getApiVersion() {
        return apiVersion;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        WebRequestContext other = (WebRequestContext) obj;
        return Objects.equals(this.requestId, other.requestId) && Objects.equals(this.traceId, other.traceId) && Objects.equals(this.tenantId, other.tenantId) && Objects.equals(this.authorization, other.authorization) && Objects.equals(this.locale, other.locale) && Objects.equals(this.clientIp, other.clientIp) && Objects.equals(this.method, other.method) && Objects.equals(this.path, other.path) && Objects.equals(this.apiVersion, other.apiVersion);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(requestId);
        result = 31 * result + Objects.hashCode(traceId);
        result = 31 * result + Objects.hashCode(tenantId);
        result = 31 * result + Objects.hashCode(authorization);
        result = 31 * result + Objects.hashCode(locale);
        result = 31 * result + Objects.hashCode(clientIp);
        result = 31 * result + Objects.hashCode(method);
        result = 31 * result + Objects.hashCode(path);
        result = 31 * result + Objects.hashCode(apiVersion);
        return result;
    }

    @Override
    public String toString() {
        return "WebRequestContext[requestId=" + requestId + ", traceId=" + traceId + ", tenantId=" + tenantId + ", authorization=" + authorization + ", locale=" + locale + ", clientIp=" + clientIp + ", method=" + method + ", path=" + path + ", apiVersion=" + apiVersion + "]";
    }
}
