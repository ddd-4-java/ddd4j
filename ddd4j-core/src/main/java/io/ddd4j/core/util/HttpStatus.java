package io.ddd4j.core.util;

/**
 * HTTP 状态码常量（纯 Java，零框架依赖）
 * <p>
 * 从 Spring HttpStatus 提取为纯 Java 常量类，供 {@link io.ddd4j.core.ApiCode} 使用。
 *
 * @author wandl
 * @since 3.4.x
 */
public final class HttpStatus {

    private HttpStatus() {
    }

    public static final int SC_BAD_REQUEST = 400;
    public static final int SC_UNAUTHORIZED = 401;
    public static final int SC_PAYMENT_REQUIRED = 402;
    public static final int SC_FORBIDDEN = 403;
    public static final int SC_NOT_FOUND = 404;
    public static final int SC_METHOD_NOT_ALLOWED = 405;
    public static final int SC_NOT_ACCEPTABLE = 406;
    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;
    public static final int SC_REQUEST_TIMEOUT = 408;
    public static final int SC_CONFLICT = 409;
    public static final int SC_GONE = 410;
    public static final int SC_LENGTH_REQUIRED = 411;
    public static final int SC_PRECONDITION_FAILED = 412;
    public static final int SC_REQUEST_TOO_LONG = 413;
    public static final int SC_REQUEST_URI_TOO_LONG = 414;
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    public static final int SC_EXPECTATION_FAILED = 417;
    public static final int SC_UNPROCESSABLE_ENTITY = 422;
    public static final int SC_LOCKED = 423;
    public static final int SC_FAILED_DEPENDENCY = 424;
    public static final int SC_UPGRADE_REQUIRED = 426;
    public static final int SC_PRECONDITION_REQUIRED = 428;
    public static final int SC_TOO_MANY_REQUESTS = 429;
    public static final int SC_REQUEST_HEADER_FIELDS_TOO_LARGE = 431;
    public static final int SC_UNAVAILABLE_FOR_LEGAL_REASONS = 451;

    public static final int SC_INTERNAL_SERVER_ERROR = 500;
    public static final int SC_NOT_IMPLEMENTED = 501;
    public static final int SC_BAD_GATEWAY = 502;
    public static final int SC_SERVICE_UNAVAILABLE = 503;
    public static final int SC_GATEWAY_TIMEOUT = 504;
    public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;
    public static final int SC_VARIANT_ALSO_NEGOTIATES = 506;
    public static final int SC_INSUFFICIENT_STORAGE = 507;
    public static final int SC_LOOP_DETECTED = 508;
    public static final int SC_BANDWIDTH_LIMIT_EXCEEDED = 509;
    public static final int SC_NOT_EXTENDED = 510;
    public static final int SC_NETWORK_AUTHENTICATION_REQUIRED = 511;
}
