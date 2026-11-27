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

import java.util.Objects;

import java.util.Locale;

/**
 * Web 框架采集到的原始请求元数据。
 */
public final class WebRequestData {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

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

    /** \u4ee3\u7406\u4f20\u5165\u7684\u539f\u59cb X-Forwarded-For \u8bf7\u6c42\u5934\u3002 */
    private final String forwardedFor;

    /** X-Real-IP \u8bf7\u6c42\u5934\u4e2d\u4ee3\u7406\u62a5\u544a\u7684\u6765\u6e90\u5730\u5740\u3002 */
    private final String realIp;

    /** \u670d\u52a1\u5668\u89c2\u5bdf\u5230\u7684\u8fde\u63a5\u5bf9\u7aef\u5730\u5740\u3002 */
    private final String remoteAddress;

    /** \u5f53\u524d HTTP \u8bf7\u6c42\u7684\u65b9\u6cd5\u3002 */
    private final String method;

    /** \u5f53\u524d\u6846\u67b6\u89e3\u6790\u51fa\u7684\u8bf7\u6c42\u8def\u5f84\u3002 */
    private final String path;

    /**
     * 返回Authorization 请求头。
     *
     * @return Authorization 请求头
     */

    public String getAuthorization() {
        return authorization;
    }

    /**
     * 返回代理转发地址。
     *
     * @return 代理转发地址
     */

    public String getForwardedFor() {
        return forwardedFor;
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
     * 返回真实 IP 地址。
     *
     * @return 真实 IP 地址
     */

    public String getRealIp() {
        return realIp;
    }

    /**
     * 返回远端地址。
     *
     * @return 远端地址
     */

    public String getRemoteAddress() {
        return remoteAddress;
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
     * 创建框架无关的原始请求数据。
     *
     * @param requestId 请求标识
     * @param traceId 跟踪标识
     * @param tenantId 租户标识
     * @param authorization Authorization 请求头
     * @param locale 区域设置
     * @param forwardedFor 代理转发地址
     * @param realIp 真实 IP 地址
     * @param remoteAddress 远端地址
     * @param method HTTP 方法
     * @param path 请求路径
     */
    @JsonCreator()
    public WebRequestData(@JsonProperty("requestId") String requestId, @JsonProperty("traceId") String traceId, @JsonProperty("tenantId") String tenantId, @JsonProperty("authorization") String authorization, @JsonProperty("locale") Locale locale, @JsonProperty("forwardedFor") String forwardedFor, @JsonProperty("realIp") String realIp, @JsonProperty("remoteAddress") String remoteAddress, @JsonProperty("method") String method, @JsonProperty("path") String path) {
        this.requestId = requestId;
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.authorization = authorization;
        this.locale = locale;
        this.forwardedFor = forwardedFor;
        this.realIp = realIp;
        this.remoteAddress = remoteAddress;
        this.method = method;
        this.path = path;
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
     * 返回代理转发地址。
     *
     * @return 代理转发地址
     */
    @JsonProperty("forwardedFor")
    public String forwardedFor() {
        return forwardedFor;
    }

    /**
     * 返回真实 IP 地址。
     *
     * @return 真实 IP 地址
     */
    @JsonProperty("realIp")
    public String realIp() {
        return realIp;
    }

    /**
     * 返回远端地址。
     *
     * @return 远端地址
     */
    @JsonProperty("remoteAddress")
    public String remoteAddress() {
        return remoteAddress;
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

    /**
     * 根据<code>requestId</code>、<code>traceId</code>、<code>tenantId</code>、<code>authorization</code>、<code>locale</code>、<code>forwardedFor</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        WebRequestData other = (WebRequestData) obj;
        return Objects.equals(this.requestId, other.requestId) && Objects.equals(this.traceId, other.traceId) && Objects.equals(this.tenantId, other.tenantId) && Objects.equals(this.authorization, other.authorization) && Objects.equals(this.locale, other.locale) && Objects.equals(this.forwardedFor, other.forwardedFor) && Objects.equals(this.realIp, other.realIp) && Objects.equals(this.remoteAddress, other.remoteAddress) && Objects.equals(this.method, other.method) && Objects.equals(this.path, other.path);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(requestId);
        result = 31 * result + Objects.hashCode(traceId);
        result = 31 * result + Objects.hashCode(tenantId);
        result = 31 * result + Objects.hashCode(authorization);
        result = 31 * result + Objects.hashCode(locale);
        result = 31 * result + Objects.hashCode(forwardedFor);
        result = 31 * result + Objects.hashCode(realIp);
        result = 31 * result + Objects.hashCode(remoteAddress);
        result = 31 * result + Objects.hashCode(method);
        result = 31 * result + Objects.hashCode(path);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>requestId</code>、<code>traceId</code>、<code>tenantId</code>、<code>authorization</code>、<code>locale</code>、<code>forwardedFor</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "WebRequestData[requestId=" + requestId + ", traceId=" + traceId + ", tenantId=" + tenantId + ", authorization=" + authorization + ", locale=" + locale + ", forwardedFor=" + forwardedFor + ", realIp=" + realIp + ", remoteAddress=" + remoteAddress + ", method=" + method + ", path=" + path + "]";
    }
}
