package io.ddd4j.web.core.context;

import java.util.Locale;

/**
 * Web 框架采集到的原始请求元数据。
 */public final class WebRequestData {

    private final String requestId;
    private final String traceId;
    private final String tenantId;
    private final String authorization;
    private final Locale locale;
    private final String forwardedFor;
    private final String realIp;
    private final String remoteAddress;
    private final String method;
    private final String path;

    public WebRequestData(String requestId, String traceId, String tenantId, String authorization,
                          Locale locale, String forwardedFor, String realIp, String remoteAddress,
                          String method, String path) {
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

    public String requestId() { return requestId; }
    public String traceId() { return traceId; }
    public String tenantId() { return tenantId; }
    public String authorization() { return authorization; }
    public Locale locale() { return locale; }
    public String forwardedFor() { return forwardedFor; }
    public String realIp() { return realIp; }
    public String remoteAddress() { return remoteAddress; }
    public String method() { return method; }
    public String path() { return path; }
}