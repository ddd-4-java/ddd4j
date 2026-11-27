package io.ddd4j.core;

/**
 * HTTP 状态码常量（纯 Java，零框架依赖）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class HttpStatus {

    // 1xx Informational
    /** HTTP 100 Continue \u72b6\u6001\u7801\u3002 */
    public static final int SC_CONTINUE = 100;
    /** HTTP 101 Switching Protocols \u72b6\u6001\u7801\u3002 */
    public static final int SC_SWITCHING_PROTOCOLS = 101;
    /** HTTP 102 Processing \u72b6\u6001\u7801\u3002 */
    public static final int SC_PROCESSING = 102;
    // 2xx Success
    /** HTTP 200 Ok \u72b6\u6001\u7801\u3002 */
    public static final int SC_OK = 200;
    /** HTTP 201 Created \u72b6\u6001\u7801\u3002 */
    public static final int SC_CREATED = 201;
    /** HTTP 202 Accepted \u72b6\u6001\u7801\u3002 */
    public static final int SC_ACCEPTED = 202;
    /** HTTP 203 Non Authoritative Information \u72b6\u6001\u7801\u3002 */
    public static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;
    /** HTTP 204 No Content \u72b6\u6001\u7801\u3002 */
    public static final int SC_NO_CONTENT = 204;
    /** HTTP 205 Reset Content \u72b6\u6001\u7801\u3002 */
    public static final int SC_RESET_CONTENT = 205;
    /** HTTP 206 Partial Content \u72b6\u6001\u7801\u3002 */
    public static final int SC_PARTIAL_CONTENT = 206;
    /** HTTP 207 Multi Status \u72b6\u6001\u7801\u3002 */
    public static final int SC_MULTI_STATUS = 207;
    // 3xx Redirection
    /** HTTP 300 Multiple Choices \u72b6\u6001\u7801\u3002 */
    public static final int SC_MULTIPLE_CHOICES = 300;
    /** HTTP 301 Moved Permanently \u72b6\u6001\u7801\u3002 */
    public static final int SC_MOVED_PERMANENTLY = 301;
    /** HTTP 302 Moved Temporarily \u72b6\u6001\u7801\u3002 */
    public static final int SC_MOVED_TEMPORARILY = 302;
    /** HTTP 303 See Other \u72b6\u6001\u7801\u3002 */
    public static final int SC_SEE_OTHER = 303;
    /** HTTP 304 Not Modified \u72b6\u6001\u7801\u3002 */
    public static final int SC_NOT_MODIFIED = 304;
    /** HTTP 305 Use Proxy \u72b6\u6001\u7801\u3002 */
    public static final int SC_USE_PROXY = 305;
    /** HTTP 307 Temporary Redirect \u72b6\u6001\u7801\u3002 */
    public static final int SC_TEMPORARY_REDIRECT = 307;
    // 4xx Client Error
    /** HTTP 400 Bad Request \u72b6\u6001\u7801\u3002 */
    public static final int SC_BAD_REQUEST = 400;
    /** HTTP 401 Unauthorized \u72b6\u6001\u7801\u3002 */
    public static final int SC_UNAUTHORIZED = 401;
    /** HTTP 402 Payment Required \u72b6\u6001\u7801\u3002 */
    public static final int SC_PAYMENT_REQUIRED = 402;
    /** HTTP 403 Forbidden \u72b6\u6001\u7801\u3002 */
    public static final int SC_FORBIDDEN = 403;
    /** HTTP 404 Not Found \u72b6\u6001\u7801\u3002 */
    public static final int SC_NOT_FOUND = 404;
    /** HTTP 405 Method Not Allowed \u72b6\u6001\u7801\u3002 */
    public static final int SC_METHOD_NOT_ALLOWED = 405;
    /** HTTP 406 Not Acceptable \u72b6\u6001\u7801\u3002 */
    public static final int SC_NOT_ACCEPTABLE = 406;
    /** HTTP 407 Proxy Authentication Required \u72b6\u6001\u7801\u3002 */
    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;
    /** HTTP 408 Request Timeout \u72b6\u6001\u7801\u3002 */
    public static final int SC_REQUEST_TIMEOUT = 408;
    /** HTTP 409 Conflict \u72b6\u6001\u7801\u3002 */
    public static final int SC_CONFLICT = 409;
    /** HTTP 410 Gone \u72b6\u6001\u7801\u3002 */
    public static final int SC_GONE = 410;
    /** HTTP 411 Length Required \u72b6\u6001\u7801\u3002 */
    public static final int SC_LENGTH_REQUIRED = 411;
    /** HTTP 412 Precondition Failed \u72b6\u6001\u7801\u3002 */
    public static final int SC_PRECONDITION_FAILED = 412;
    /** HTTP 413 Request Too Long \u72b6\u6001\u7801\u3002 */
    public static final int SC_REQUEST_TOO_LONG = 413;
    /** HTTP 414 Request URI Too Long \u72b6\u6001\u7801\u3002 */
    public static final int SC_REQUEST_URI_TOO_LONG = 414;
    /** HTTP 415 Unsupported Media Type \u72b6\u6001\u7801\u3002 */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;
    /** HTTP 416 Requested Range Not Satisfiable \u72b6\u6001\u7801\u3002 */
    public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    /** HTTP 417 Expectation Failed \u72b6\u6001\u7801\u3002 */
    public static final int SC_EXPECTATION_FAILED = 417;
    /** HTTP 422 Unprocessable Entity \u72b6\u6001\u7801\u3002 */
    public static final int SC_UNPROCESSABLE_ENTITY = 422;
    /** HTTP 423 Locked \u72b6\u6001\u7801\u3002 */
    public static final int SC_LOCKED = 423;
    /** HTTP 424 Failed Dependency \u72b6\u6001\u7801\u3002 */
    public static final int SC_FAILED_DEPENDENCY = 424;
    /** HTTP 426 Upgrade Required \u72b6\u6001\u7801\u3002 */
    public static final int SC_UPGRADE_REQUIRED = 426;
    /** HTTP 428 Precondition Required \u72b6\u6001\u7801\u3002 */
    public static final int SC_PRECONDITION_REQUIRED = 428;
    /** HTTP 429 Too Many Requests \u72b6\u6001\u7801\u3002 */
    public static final int SC_TOO_MANY_REQUESTS = 429;
    /** HTTP 431 Request Header Fields Too Large \u72b6\u6001\u7801\u3002 */
    public static final int SC_REQUEST_HEADER_FIELDS_TOO_LARGE = 431;
    /** HTTP 451 Unavailable For Legal Reasons \u72b6\u6001\u7801\u3002 */
    public static final int SC_UNAVAILABLE_FOR_LEGAL_REASONS = 451;
    // 5xx Server Error
    /** HTTP 500 Internal Server Error \u72b6\u6001\u7801\u3002 */
    public static final int SC_INTERNAL_SERVER_ERROR = 500;
    /** HTTP 501 Not Implemented \u72b6\u6001\u7801\u3002 */
    public static final int SC_NOT_IMPLEMENTED = 501;
    /** HTTP 502 Bad Gateway \u72b6\u6001\u7801\u3002 */
    public static final int SC_BAD_GATEWAY = 502;
    /** HTTP 503 Service Unavailable \u72b6\u6001\u7801\u3002 */
    public static final int SC_SERVICE_UNAVAILABLE = 503;
    /** HTTP 504 Gateway Timeout \u72b6\u6001\u7801\u3002 */
    public static final int SC_GATEWAY_TIMEOUT = 504;
    /** HTTP 505 HTTP Version Not Supported \u72b6\u6001\u7801\u3002 */
    public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;
    /** HTTP 506 Variant Also Negotiates \u72b6\u6001\u7801\u3002 */
    public static final int SC_VARIANT_ALSO_NEGOTIATES = 506;
    /** HTTP 507 Insufficient Storage \u72b6\u6001\u7801\u3002 */
    public static final int SC_INSUFFICIENT_STORAGE = 507;
    /** HTTP 508 Loop Detected \u72b6\u6001\u7801\u3002 */
    public static final int SC_LOOP_DETECTED = 508;
    /** HTTP 509 Bandwidth Limit Exceeded \u72b6\u6001\u7801\u3002 */
    public static final int SC_BANDWIDTH_LIMIT_EXCEEDED = 509;
    /** HTTP 510 Not Extended \u72b6\u6001\u7801\u3002 */
    public static final int SC_NOT_EXTENDED = 510;
    /** HTTP 511 Network Authentication Required \u72b6\u6001\u7801\u3002 */
    public static final int SC_NETWORK_AUTHENTICATION_REQUIRED = 511;

    /** \u9650\u5236\u72b6\u6001\u7801\u5de5\u5177\u7c7b\u5b9e\u4f8b\u5316\u3002 */
    private HttpStatus() {
        // 工具类，禁止实例化
    }
}
