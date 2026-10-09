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
import io.ddd4j.web.core.version.ApiVersion;

import java.util.Locale;
import java.util.Objects;

/**
 * HTTP 请求在 ddd4j 内部的框架无关表示。
 */public final class WebRequestContext {

    private final String requestId;
    private final String traceId;
    private final String tenantId;
    private final String authorization;
    private final Locale locale;
    private final String clientIp;
    private final String method;
    private final String path;

    /**
     * 请求解析出的 API 版本，未启用版本解析或解析未接线时为 null。
     */
    private final ApiVersion apiVersion;

/**
 * HTTP 请求在 ddd4j 内部的框架无关表示。
 * @param requestId 请求标识
 * @param traceId 链路追踪标识
 * @param tenantId 租户标识
 * @param authorization 授权信息
 * @param locale 区域与语言设置
 * @param clientIp 字符串参数
 * @param method 方法
 * @param path 路径
 */

    /**
     * 未携带 API 版本的兼容构造器：委托九参构造器并传入 null 版本，
     * 旧调用方行为与接线前完全一致。
     *
     * @param requestId 请求标识
     * @param traceId 链路追踪标识
     * @param tenantId 租户标识
     * @param authorization 授权信息
     * @param locale 区域与语言设置
     * @param clientIp 客户端 IP
     * @param method HTTP 方法
     * @param path 路径
     */
    public WebRequestContext(String requestId, String traceId, String tenantId, String authorization,
                             Locale locale, String clientIp, String method, String path) {
        this(requestId, traceId, tenantId, authorization, locale, clientIp, method, path, null);
    }

    /**
     * 携带 API 版本的完整构造器。
     *
     * @param requestId 请求标识
     * @param traceId 链路追踪标识
     * @param tenantId 租户标识
     * @param authorization 授权信息
     * @param locale 区域与语言设置
     * @param clientIp 客户端 IP
     * @param method HTTP 方法
     * @param path 路径
     * @param apiVersion 请求解析出的 API 版本，可为 null（未接线时）
     */
    public WebRequestContext(String requestId, String traceId, String tenantId, String authorization,
                             Locale locale, String clientIp, String method, String path, ApiVersion apiVersion) {
        this.requestId = StrKit.isBlank(requestId) ? null : requestId;
        this.traceId = StrKit.isBlank(traceId) ? this.requestId : traceId;
        this.tenantId = tenantId;
        this.authorization = authorization;
        this.locale = Objects.isNull(locale) ? Locale.getDefault() : locale;
        this.clientIp = clientIp;
        this.method = StrKit.isBlank(method) ? null : method.toUpperCase(Locale.ROOT);
        this.path = StrKit.isBlank(path) ? "/" : path;
        this.apiVersion = apiVersion;
    }

    public String requestId() { return requestId; }
    public String traceId() { return traceId; }
    public String tenantId() { return tenantId; }
    public String authorization() { return authorization; }
    public Locale locale() { return locale; }
    public String clientIp() { return clientIp; }
    public String method() { return method; }
    public String path() { return path; }

    /**
     * 请求解析出的 API 版本（函数式风格访问器）。
     *
     * @return API 版本，未接线或未携带时为 null
     */
    public ApiVersion apiVersion() {
        return apiVersion;
    }

    /**
     * 请求解析出的 API 版本（JavaBean 风格访问器）。
     *
     * @return API 版本，未接线或未携带时为 null
     */
    public ApiVersion getApiVersion() {
        return apiVersion;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAuthorization() {
        return authorization;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /**
     * 判断当前请求上下文与指定对象是否相等：八个基础字段加可选 {@code apiVersion} 全部参与比较。
     *
     * @param o 待比较的对象
     * @return 与指定对象相等返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebRequestContext)) {
            return false;
        }
        WebRequestContext that = (WebRequestContext) o;
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(traceId, that.traceId)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(authorization, that.authorization)
                && Objects.equals(locale, that.locale)
                && Objects.equals(clientIp, that.clientIp)
                && Objects.equals(method, that.method)
                && Objects.equals(path, that.path)
                && Objects.equals(apiVersion, that.apiVersion);
    }

    /**
     * 返回请求上下文的哈希码：八个基础字段加可选 {@code apiVersion} 全部参与计算。
     *
     * @return 请求上下文的哈希码
     */
    @Override
    public int hashCode() {
        int result = Objects.hashCode(requestId);
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

    /**
     * 返回请求上下文的字符串表示，包含解析出的 {@code apiVersion}。
     *
     * @return 形如 {@code WebRequestContext[... , apiVersion=...]} 的字符串
     */
    @Override
    public String toString() {
        return "WebRequestContext[requestId=" + requestId + ", traceId=" + traceId + ", tenantId=" + tenantId + ", authorization=" + authorization + ", locale=" + locale + ", clientIp=" + clientIp + ", method=" + method + ", path=" + path + ", apiVersion=" + apiVersion + "]";
    }
}
