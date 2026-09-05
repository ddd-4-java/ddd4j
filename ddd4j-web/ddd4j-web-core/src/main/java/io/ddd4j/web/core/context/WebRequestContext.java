package io.ddd4j.web.core.context;

import io.ddd4j.kit.lang.StrKit;

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

    public WebRequestContext(String requestId, String traceId, String tenantId, String authorization,
                             Locale locale, String clientIp, String method, String path) {
        this.requestId = StrKit.isBlank(requestId) ? null : requestId;
        this.traceId = StrKit.isBlank(traceId) ? this.requestId : traceId;
        this.tenantId = tenantId;
        this.authorization = authorization;
        this.locale = Objects.isNull(locale) ? Locale.getDefault() : locale;
        this.clientIp = clientIp;
        this.method = StrKit.isBlank(method) ? null : method.toUpperCase(Locale.ROOT);
        this.path = StrKit.isBlank(path) ? "/" : path;
    }

    public String requestId() { return requestId; }
    public String traceId() { return traceId; }
    public String tenantId() { return tenantId; }
    public String authorization() { return authorization; }
    public Locale locale() { return locale; }
    public String clientIp() { return clientIp; }
    public String method() { return method; }
    public String path() { return path; }
}