package io.ddd4j.web.core.context;

/**
 * ddd4j Web 适配器共同识别的标准与扩展请求头。
 *
 * <p>2026-09-04 从 2.0.x 复制补齐（1.0.x 之前缺失，导致 ddd4j-web-testkit 编译失败）。</p>
 */
public final class WebHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String FORWARDED_FOR = "X-Forwarded-For";

    private WebHeaders() {
    }
}