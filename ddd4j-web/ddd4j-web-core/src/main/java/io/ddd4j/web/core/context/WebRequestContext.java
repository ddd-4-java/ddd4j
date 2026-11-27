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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.version.ApiVersion;

import java.util.Locale;
import java.util.Objects;

/**
 * HTTP 请求在 ddd4j 内部的框架无关表示。
 */
public final class WebRequestContext {

    /** \u5f53\u524d\u8bf7\u6c42\u7684\u6807\u8bc6\u3002 */
    private final String requestId;
    /** \u7528\u4e8e\u5173\u8054\u4e00\u6761\u8c03\u7528\u94fe\u7684\u8ddf\u8e2a\u6807\u8bc6\u3002 */
    private final String traceId;
    /** \u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    private final String tenantId;
    /** \u5f53\u524d\u8bf7\u6c42\u4e2d\u7528\u4e8e\u8ba4\u8bc1\u7684 Authorization \u5934\u539f\u503c\u3002 */
    private final String authorization;
    /** \u5f53\u524d\u8bf7\u6c42\u4f7f\u7528\u7684\u8bed\u8a00\u53ca\u533a\u57df\u914d\u7f6e\u3002 */
    private final Locale locale;
    /** \u6839\u636e\u539f\u751f\u8fde\u63a5\u548c\u53ef\u4fe1\u8f6c\u53d1\u5934\u89e3\u6790\u7684\u5ba2\u6237\u7aef\u5730\u5740\u3002 */
    private final String clientIp;
    /** \u5f53\u524d HTTP \u8bf7\u6c42\u7684\u65b9\u6cd5\u3002 */
    private final String method;
    /** \u5f53\u524d\u6846\u67b6\u89e3\u6790\u51fa\u7684\u8bf7\u6c42\u8def\u5f84\u3002 */
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
    @JsonCreator
    public WebRequestContext(@JsonProperty("requestId") String requestId, @JsonProperty("traceId") String traceId,
                             @JsonProperty("tenantId") String tenantId, @JsonProperty("authorization") String authorization,
                             @JsonProperty("locale") Locale locale, @JsonProperty("clientIp") String clientIp,
                             @JsonProperty("method") String method, @JsonProperty("path") String path,
                             @JsonProperty("apiVersion") ApiVersion apiVersion) {
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

    /** @return 请求标识 */
    public String requestId() { return requestId; }
    /** @return 追踪标识 */
    public String traceId() { return traceId; }
    /** @return 租户标识 */
    public String tenantId() { return tenantId; }
    /** @return 授权头 */
    public String authorization() { return authorization; }
    /** @return 语言区域 */
    public Locale locale() { return locale; }
    /** @return 客户端 IP */
    public String clientIp() { return clientIp; }
    /** @return HTTP 方法 */
    public String method() { return method; }
    /** @return 请求路径 */
    public String path() { return path; }

    /**
     * 请求解析出的 API 版本（函数式风格访问器）。
     *
     * @return API 版本，未接线或未携带时为 null
     */
    @JsonProperty("apiVersion")
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

    /** @return 请求标识 */
    public String getRequestId() {
        return requestId;
    }

    /** @return 追踪标识 */
    public String getTraceId() {
        return traceId;
    }

    /** @return 租户标识 */
    public String getTenantId() {
        return tenantId;
    }

    /** @return 授权头 */
    public String getAuthorization() {
        return authorization;
    }

    /** @return 语言区域 */
    public Locale getLocale() {
        return locale;
    }

    /** @return 客户端 IP */
    public String getClientIp() {
        return clientIp;
    }

    /** @return HTTP 方法 */
    public String getMethod() {
        return method;
    }

    /** @return 请求路径 */
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
