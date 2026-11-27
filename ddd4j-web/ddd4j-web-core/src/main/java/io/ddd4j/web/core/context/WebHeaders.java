package io.ddd4j.web.core.context;

/**
 * ddd4j Web 适配器共同识别的标准与扩展请求头。
 */
public final class WebHeaders {

    /** \u5e38\u91cf\u503c {@code "Authorization"}\u3002 */
    public static final String AUTHORIZATION = "Authorization";
    /** \u5e38\u91cf\u503c {@code "Idempotency-Key"}\u3002 */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** \u5e38\u91cf\u503c {@code "X-Request-Id"}\u3002 */
    public static final String REQUEST_ID = "X-Request-Id";
    /** \u5e38\u91cf\u503c {@code "X-Trace-Id"}\u3002 */
    public static final String TRACE_ID = "X-Trace-Id";
    /** \u5e38\u91cf\u503c {@code "X-Tenant-Id"}\u3002 */
    public static final String TENANT_ID = "X-Tenant-Id";
    /** \u5e38\u91cf\u503c {@code "X-Forwarded-For"}\u3002 */
    public static final String FORWARDED_FOR = "X-Forwarded-For";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebHeaders</code> \u5b9e\u4f8b\u3002 */
    private WebHeaders() {
    }
}
