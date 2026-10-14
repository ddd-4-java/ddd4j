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
    /**
     * 100（继续）：客户端应继续发送请求主体。
     */
    public static final int SC_CONTINUE = 100;
    /**
     * 101（切换协议）：服务端同意切换到另一协议。
     */
    public static final int SC_SWITCHING_PROTOCOLS = 101;
    /**
     * 102（处理中）：服务器已收到并正在处理请求，尚无最终响应。
     */
    public static final int SC_PROCESSING = 102;
    // 2xx Success
    /**
     * 200（成功）：请求已成功处理。
     */
    public static final int SC_OK = 200;
    /**
     * 201（已创建）：请求已成功并创建了新资源。
     */
    public static final int SC_CREATED = 201;
    /**
     * 202（已接受）：请求已接受但尚未处理完成。
     */
    public static final int SC_ACCEPTED = 202;
    /**
     * 203（非授权信息）：返回的元信息不是来自源服务器的权威信息。
     */
    public static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;
    /**
     * 204（无内容）：请求成功但没有返回内容。
     */
    public static final int SC_NO_CONTENT = 204;
    /**
     * 205（重置内容）：请求成功，客户端应重置文档视图。
     */
    public static final int SC_RESET_CONTENT = 205;
    /**
     * 206（部分内容）：服务器只返回了请求的部分内容。
     */
    public static final int SC_PARTIAL_CONTENT = 206;
    /**
     * 207（多状态）：响应体中可能包含多个独立的状态码。
     */
    public static final int SC_MULTI_STATUS = 207;
    // 3xx Redirection
    /**
     * 300（多种选择）：请求可对应多个资源，需要客户端选择。
     */
    public static final int SC_MULTIPLE_CHOICES = 300;
    /**
     * 301（永久移动）：请求的资源已被永久移动到新地址。
     */
    public static final int SC_MOVED_PERMANENTLY = 301;
    /**
     * 302（临时移动）：请求的资源临时位于其他地址。
     */
    public static final int SC_MOVED_TEMPORARILY = 302;
    /**
     * 303（查看其他位置）：应使用 GET 方法到指定位置获取资源。
     */
    public static final int SC_SEE_OTHER = 303;
    /**
     * 304（未修改）：资源自上次请求后未被修改，可使用缓存副本。
     */
    public static final int SC_NOT_MODIFIED = 304;
    /**
     * 305（使用代理）：请求的资源必须通过指定代理访问。
     */
    public static final int SC_USE_PROXY = 305;
    /**
     * 307（临时重定向）：请求的资源临时位于其他地址，方法不得改变。
     */
    public static final int SC_TEMPORARY_REDIRECT = 307;
    // 4xx Client Error
    /**
     * 400（错误请求）：服务器无法解析请求语法。
     */
    public static final int SC_BAD_REQUEST = 400;
    /**
     * 401（未授权）：请求需要身份验证。
     */
    public static final int SC_UNAUTHORIZED = 401;
    /** 字段说明。 */
    public static final int SC_PAYMENT_REQUIRED = 402;
    /** 字段说明。 */
    public static final int SC_FORBIDDEN = 403;
    /** 字段说明。 */
    public static final int SC_NOT_FOUND = 404;
    /** 字段说明。 */
    public static final int SC_METHOD_NOT_ALLOWED = 405;
    /** 字段说明。 */
    public static final int SC_NOT_ACCEPTABLE = 406;
    /** 字段说明。 */
    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;
    /** 字段说明。 */
    public static final int SC_REQUEST_TIMEOUT = 408;
    /** 字段说明。 */
    public static final int SC_CONFLICT = 409;
    /** 字段说明。 */
    public static final int SC_GONE = 410;
    /** 字段说明。 */
    public static final int SC_LENGTH_REQUIRED = 411;
    /** 字段说明。 */
    public static final int SC_PRECONDITION_FAILED = 412;
    /** 字段说明。 */
    public static final int SC_REQUEST_TOO_LONG = 413;
    /** 字段说明。 */
    public static final int SC_REQUEST_URI_TOO_LONG = 414;
    /** 字段说明。 */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;
    /** 字段说明。 */
    public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    /** 字段说明。 */
    public static final int SC_EXPECTATION_FAILED = 417;
    /** 字段说明。 */
    public static final int SC_UNPROCESSABLE_ENTITY = 422;
    /** 字段说明。 */
    public static final int SC_LOCKED = 423;
    /** 字段说明。 */
    public static final int SC_FAILED_DEPENDENCY = 424;
    /** 字段说明。 */
    public static final int SC_UPGRADE_REQUIRED = 426;
    /** 字段说明。 */
    public static final int SC_PRECONDITION_REQUIRED = 428;
    /** 字段说明。 */
    public static final int SC_TOO_MANY_REQUESTS = 429;
    /** 字段说明。 */
    public static final int SC_REQUEST_HEADER_FIELDS_TOO_LARGE = 431;
    /** 字段说明。 */
    public static final int SC_UNAVAILABLE_FOR_LEGAL_REASONS = 451;
    // 5xx Server Error
    /** 字段说明。 */
    public static final int SC_INTERNAL_SERVER_ERROR = 500;
    /** 字段说明。 */
    public static final int SC_NOT_IMPLEMENTED = 501;
    /** 字段说明。 */
    public static final int SC_BAD_GATEWAY = 502;
    /** 字段说明。 */
    public static final int SC_SERVICE_UNAVAILABLE = 503;
    /** 字段说明。 */
    public static final int SC_GATEWAY_TIMEOUT = 504;
    /** 字段说明。 */
    public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;
    /** 字段说明。 */
    public static final int SC_VARIANT_ALSO_NEGOTIATES = 506;
    /** 字段说明。 */
    public static final int SC_INSUFFICIENT_STORAGE = 507;
    /** 字段说明。 */
    public static final int SC_LOOP_DETECTED = 508;
    /** 字段说明。 */
    public static final int SC_BANDWIDTH_LIMIT_EXCEEDED = 509;
    /** 字段说明。 */
    public static final int SC_NOT_EXTENDED = 510;
    /** 字段说明。 */
    public static final int SC_NETWORK_AUTHENTICATION_REQUIRED = 511;

    private HttpStatus() {
        // 工具类，禁止实例化
    }
}
