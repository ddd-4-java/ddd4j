package io.ddd4j.web.core.context;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.kit.lang.StrKit;

import java.util.Locale;
import java.util.Objects;

/**
 * HTTP 请求在 ddd4j 内部的框架无关表示。
 */
public final class WebRequestContext {

    /** Java 序列化版本标识。 */
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

    /** \u6839\u636e\u539f\u751f\u8fde\u63a5\u548c\u53ef\u4fe1\u8f6c\u53d1\u5934\u89e3\u6790\u7684\u5ba2\u6237\u7aef\u5730\u5740\u3002 */
    private final String clientIp;

    /** \u5f53\u524d HTTP \u8bf7\u6c42\u7684\u65b9\u6cd5\u3002 */
    private final String method;

    /** \u5f53\u524d\u6846\u67b6\u89e3\u6790\u51fa\u7684\u8bf7\u6c42\u8def\u5f84\u3002 */
    private final String path;

    /**
     * \u672a\u643a\u5e26 API \u7248\u672c\u7684\u517c\u5bb9\u6784\u9020\u5668\uff1a\u59d4\u6258\u4e5d\u53c2\u6784\u9020\u5668\u5e76\u4f20\u5165 null \u7248\u672c\uff0c
     *  \u65e7\u8c03\u7528\u65b9\u884c\u4e3a\u4e0e\u63a5\u7ebf\u524d\u5b8c\u5168\u4e00\u81f4\u3002
     * @param requestId \u8bf7\u6c42\u6807\u8bc6
     * @param traceId \u94fe\u8def\u8ffd\u8e2a\u6807\u8bc6
     * @param tenantId \u79df\u6237\u6807\u8bc6
     * @param authorization \u6388\u6743\u4fe1\u606f
     * @param locale \u533a\u57df\u4e0e\u8bed\u8a00\u8bbe\u7f6e
     * @param clientIp \u5ba2\u6237\u7aef IP
     * @param method HTTP \u65b9\u6cd5
     * @param path \u8def\u5f84
     */
    @JsonCreator()
    public WebRequestContext(@JsonProperty("requestId") String requestId, @JsonProperty("traceId") String traceId, @JsonProperty("tenantId") String tenantId, @JsonProperty("authorization") String authorization, @JsonProperty("locale") Locale locale, @JsonProperty("clientIp") String clientIp, @JsonProperty("method") String method, @JsonProperty("path") String path) {
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
    }

    /** @return \u8bf7\u6c42\u6807\u8bc6 */
    @JsonProperty("requestId")
    public String requestId() {
        return requestId;
    }

    /** @return \u8ffd\u8e2a\u6807\u8bc6 */
    @JsonProperty("traceId")
    public String traceId() {
        return traceId;
    }

    /** @return \u79df\u6237\u6807\u8bc6 */
    @JsonProperty("tenantId")
    public String tenantId() {
        return tenantId;
    }

    /** @return \u6388\u6743\u5934 */
    @JsonProperty("authorization")
    public String authorization() {
        return authorization;
    }

    /** @return \u8bed\u8a00\u533a\u57df */
    @JsonProperty("locale")
    public Locale locale() {
        return locale;
    }

    /** @return \u5ba2\u6237\u7aef IP */
    @JsonProperty("clientIp")
    public String clientIp() {
        return clientIp;
    }

    /** @return HTTP \u65b9\u6cd5 */
    @JsonProperty("method")
    public String method() {
        return method;
    }

    /** @return \u8bf7\u6c42\u8def\u5f84 */
    @JsonProperty("path")
    public String path() {
        return path;
    }

    /**
     * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
     *
     * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
     * @return 按条件返回 <code>true</code>、<code>false</code>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        WebRequestContext other = (WebRequestContext) obj;
        return Objects.equals(this.requestId, other.requestId) && Objects.equals(this.traceId, other.traceId) && Objects.equals(this.tenantId, other.tenantId) && Objects.equals(this.authorization, other.authorization) && Objects.equals(this.locale, other.locale) && Objects.equals(this.clientIp, other.clientIp) && Objects.equals(this.method, other.method) && Objects.equals(this.path, other.path);
    }

    /**
     * \u8fd4\u56de\u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u54c8\u5e0c\u7801\uff1a\u516b\u4e2a\u57fa\u7840\u5b57\u6bb5\u52a0\u53ef\u9009 {@code apiVersion} \u5168\u90e8\u53c2\u4e0e\u8ba1\u7b97\u3002
     * @return \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u54c8\u5e0c\u7801
     */
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
        return result;
    }

    /**
     * \u8fd4\u56de\u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u5b57\u7b26\u4e32\u8868\u793a\uff0c\u5305\u542b\u89e3\u6790\u51fa\u7684 {@code apiVersion}\u3002
     * @return \u5f62\u5982 {@code WebRequestContext[... , apiVersion=...]} \u7684\u5b57\u7b26\u4e32
     */
    @Override
    public String toString() {
        return "WebRequestContext[requestId=" + requestId + ", traceId=" + traceId + ", tenantId=" + tenantId + ", authorization=" + authorization + ", locale=" + locale + ", clientIp=" + clientIp + ", method=" + method + ", path=" + path + "]";
    }
}
