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
package io.ddd4j.core.constant;

/**
 * HTTP 状态码常量（纯 Java，零框架依赖）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class HttpStatus {

    // 1xx Informational
    /** HTTP 100 Continue 状态码。 */
    public static final int SC_CONTINUE = 100;
    /** HTTP 101 Switching Protocols 状态码。 */
    public static final int SC_SWITCHING_PROTOCOLS = 101;
    /** HTTP 102 Processing 状态码。 */
    public static final int SC_PROCESSING = 102;
    // 2xx Success
    /** HTTP 200 Ok 状态码。 */
    public static final int SC_OK = 200;
    /** HTTP 201 Created 状态码。 */
    public static final int SC_CREATED = 201;
    /** HTTP 202 Accepted 状态码。 */
    public static final int SC_ACCEPTED = 202;
    /** HTTP 203 Non Authoritative Information 状态码。 */
    public static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;
    /** HTTP 204 No Content 状态码。 */
    public static final int SC_NO_CONTENT = 204;
    /** HTTP 205 Reset Content 状态码。 */
    public static final int SC_RESET_CONTENT = 205;
    /** HTTP 206 Partial Content 状态码。 */
    public static final int SC_PARTIAL_CONTENT = 206;
    /** HTTP 207 Multi Status 状态码。 */
    public static final int SC_MULTI_STATUS = 207;
    // 3xx Redirection
    /** HTTP 300 Multiple Choices 状态码。 */
    public static final int SC_MULTIPLE_CHOICES = 300;
    /** HTTP 301 Moved Permanently 状态码。 */
    public static final int SC_MOVED_PERMANENTLY = 301;
    /** HTTP 302 Moved Temporarily 状态码。 */
    public static final int SC_MOVED_TEMPORARILY = 302;
    /** HTTP 303 See Other 状态码。 */
    public static final int SC_SEE_OTHER = 303;
    /** HTTP 304 Not Modified 状态码。 */
    public static final int SC_NOT_MODIFIED = 304;
    /** HTTP 305 Use Proxy 状态码。 */
    public static final int SC_USE_PROXY = 305;
    /** HTTP 307 Temporary Redirect 状态码。 */
    public static final int SC_TEMPORARY_REDIRECT = 307;
    // 4xx Client Error
    /** HTTP 400 Bad Request 状态码。 */
    public static final int SC_BAD_REQUEST = 400;
    /** HTTP 401 Unauthorized 状态码。 */
    public static final int SC_UNAUTHORIZED = 401;
    /** HTTP 402 Payment Required 状态码。 */
    public static final int SC_PAYMENT_REQUIRED = 402;
    /** HTTP 403 Forbidden 状态码。 */
    public static final int SC_FORBIDDEN = 403;
    /** HTTP 404 Not Found 状态码。 */
    public static final int SC_NOT_FOUND = 404;
    /** HTTP 405 Method Not Allowed 状态码。 */
    public static final int SC_METHOD_NOT_ALLOWED = 405;
    /** HTTP 406 Not Acceptable 状态码。 */
    public static final int SC_NOT_ACCEPTABLE = 406;
    /** HTTP 407 Proxy Authentication Required 状态码。 */
    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;
    /** HTTP 408 Request Timeout 状态码。 */
    public static final int SC_REQUEST_TIMEOUT = 408;
    /** HTTP 409 Conflict 状态码。 */
    public static final int SC_CONFLICT = 409;
    /** HTTP 410 Gone 状态码。 */
    public static final int SC_GONE = 410;
    /** HTTP 411 Length Required 状态码。 */
    public static final int SC_LENGTH_REQUIRED = 411;
    /** HTTP 412 Precondition Failed 状态码。 */
    public static final int SC_PRECONDITION_FAILED = 412;
    /** HTTP 413 Request Too Long 状态码。 */
    public static final int SC_REQUEST_TOO_LONG = 413;
    /** HTTP 414 Request URI Too Long 状态码。 */
    public static final int SC_REQUEST_URI_TOO_LONG = 414;
    /** HTTP 415 Unsupported Media Type 状态码。 */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;
    /** HTTP 416 Requested Range Not Satisfiable 状态码。 */
    public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    /** HTTP 417 Expectation Failed 状态码。 */
    public static final int SC_EXPECTATION_FAILED = 417;
    /** HTTP 422 Unprocessable Entity 状态码。 */
    public static final int SC_UNPROCESSABLE_ENTITY = 422;
    /** HTTP 423 Locked 状态码。 */
    public static final int SC_LOCKED = 423;
    /** HTTP 424 Failed Dependency 状态码。 */
    public static final int SC_FAILED_DEPENDENCY = 424;
    /** HTTP 426 Upgrade Required 状态码。 */
    public static final int SC_UPGRADE_REQUIRED = 426;
    /** HTTP 428 Precondition Required 状态码。 */
    public static final int SC_PRECONDITION_REQUIRED = 428;
    /** HTTP 429 Too Many Requests 状态码。 */
    public static final int SC_TOO_MANY_REQUESTS = 429;
    /** HTTP 431 Request Header Fields Too Large 状态码。 */
    public static final int SC_REQUEST_HEADER_FIELDS_TOO_LARGE = 431;
    /** HTTP 451 Unavailable For Legal Reasons 状态码。 */
    public static final int SC_UNAVAILABLE_FOR_LEGAL_REASONS = 451;
    // 5xx Server Error
    /** HTTP 500 Internal Server Error 状态码。 */
    public static final int SC_INTERNAL_SERVER_ERROR = 500;
    /** HTTP 501 Not Implemented 状态码。 */
    public static final int SC_NOT_IMPLEMENTED = 501;
    /** HTTP 502 Bad Gateway 状态码。 */
    public static final int SC_BAD_GATEWAY = 502;
    /** HTTP 503 Service Unavailable 状态码。 */
    public static final int SC_SERVICE_UNAVAILABLE = 503;
    /** HTTP 504 Gateway Timeout 状态码。 */
    public static final int SC_GATEWAY_TIMEOUT = 504;
    /** HTTP 505 HTTP Version Not Supported 状态码。 */
    public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;
    /** HTTP 506 Variant Also Negotiates 状态码。 */
    public static final int SC_VARIANT_ALSO_NEGOTIATES = 506;
    /** HTTP 507 Insufficient Storage 状态码。 */
    public static final int SC_INSUFFICIENT_STORAGE = 507;
    /** HTTP 508 Loop Detected 状态码。 */
    public static final int SC_LOOP_DETECTED = 508;
    /** HTTP 509 Bandwidth Limit Exceeded 状态码。 */
    public static final int SC_BANDWIDTH_LIMIT_EXCEEDED = 509;
    /** HTTP 510 Not Extended 状态码。 */
    public static final int SC_NOT_EXTENDED = 510;
    /** HTTP 511 Network Authentication Required 状态码。 */
    public static final int SC_NETWORK_AUTHENTICATION_REQUIRED = 511;

    private HttpStatus() {
        // 工具类，禁止实例化
    }
}
