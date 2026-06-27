/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.api;

import io.ddd4j.core.api.util.HttpStatus;

/**
 * Enumeration of Api Code（纯 Java，无框架依赖）
 */
public enum ApiCode implements CustomApiCode {

    // --- 2xx Client Error ---
    SC_SUCCESS(ApiCodeValue.SC_SUCCESS, Constants.RT_SUCCESS, "请求成功"),

    // --- 4xx Client Error ---
    SC_BAD_REQUEST(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "请求语法错误"),
    SC_UNAUTHORIZED(HttpStatus.SC_UNAUTHORIZED, Constants.RT_FAIL, "请求要求身份验证"),
    SC_FORBIDDEN(HttpStatus.SC_FORBIDDEN, Constants.RT_FAIL, "请求被拒绝"),
    SC_NOT_FOUND(HttpStatus.SC_NOT_FOUND, Constants.RT_FAIL, "请求的资源或接口不存在"),
    SC_METHOD_NOT_ALLOWED(HttpStatus.SC_METHOD_NOT_ALLOWED, Constants.RT_FAIL, "客户端请求中的方法被禁止"),
    SC_NOT_ACCEPTABLE(HttpStatus.SC_NOT_ACCEPTABLE, Constants.RT_FAIL, "服务器无法根据客户端请求的内容特性完成请求"),
    SC_PROXY_AUTHENTICATION_REQUIRED(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, Constants.RT_FAIL, "要求进行代理身份验证"),
    SC_REQUEST_TIMEOUT(HttpStatus.SC_REQUEST_TIMEOUT, Constants.RT_FAIL, "服务器等候请求时发生超时"),
    SC_CONFLICT(HttpStatus.SC_CONFLICT, Constants.RT_FAIL, "服务器找不到请求的地址"),
    SC_GONE(HttpStatus.SC_GONE, Constants.RT_FAIL, "服务器找不到请求的地址"),
    SC_LENGTH_REQUIRED(HttpStatus.SC_LENGTH_REQUIRED, Constants.RT_FAIL, "服务器拒绝接受不带Content-Length请求头的客户端请求"),
    SC_PRECONDITION_FAILED(HttpStatus.SC_PRECONDITION_FAILED, Constants.RT_FAIL, "客户端请求信息的先决条件错误"),
    SC_REQUEST_TOO_LONG(HttpStatus.SC_REQUEST_TOO_LONG, Constants.RT_FAIL, "服务器无法处理请求，因为请求实体过大，超出服务器的处理能力"),
    SC_UNSUPPORTED_MEDIA_TYPE(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, Constants.RT_FAIL, "不支持的 Content-Type 类型"),
    SC_REQUESTED_RANGE_NOT_SATISFIABLE(HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE, Constants.RT_FAIL, "客户端请求的范围无效"),
    SC_EXPECTATION_FAILED(HttpStatus.SC_EXPECTATION_FAILED, Constants.RT_FAIL, "服务器无法满足Expect的请求头信息"),
    SC_UNPROCESSABLE_ENTITY(HttpStatus.SC_UNPROCESSABLE_ENTITY, Constants.RT_FAIL, "无法处理的请求实体"),
    SC_LOCKED(HttpStatus.SC_LOCKED, Constants.RT_FAIL, "当前资源被锁定 "),
    SC_FAILED_DEPENDENCY(HttpStatus.SC_FAILED_DEPENDENCY, Constants.RT_FAIL, "依赖导致的失败"),
    SC_UPGRADE_REQUIRED(HttpStatus.SC_UPGRADE_REQUIRED, Constants.RT_FAIL, "客户端应当切换到TLS/1.0"),
    SC_PRECONDITION_REQUIRED(HttpStatus.SC_PRECONDITION_REQUIRED, Constants.RT_FAIL, "要求先决条件"),
    SC_TOO_MANY_REQUESTS(HttpStatus.SC_TOO_MANY_REQUESTS, Constants.RT_FAIL, "太多请求"),
    SC_REQUEST_HEADER_FIELDS_TOO_LARGE(HttpStatus.SC_REQUEST_HEADER_FIELDS_TOO_LARGE, Constants.RT_FAIL, "请求头字段太大"),
    SC_UNAVAILABLE_FOR_LEGAL_REASONS(HttpStatus.SC_UNAVAILABLE_FOR_LEGAL_REASONS, Constants.RT_FAIL, "该请求因法律原因不可用"),

    // --- Custom 4xx Client Error ---
    SC_TYPE_MISMATCH(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "参数类型不匹配"),
    SC_MISSING_MATRIX_VARIABLE(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "缺少矩阵变量"),
    SC_MISSING_PATH_VARIABLE(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "缺少URI模板变量"),
    SC_MISSING_REQUEST_COOKIE(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "缺少Cookie变量"),
    SC_MISSING_REQUEST_HEADER(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "缺少请求头"),
    SC_MISSING_REQUEST_PARAM(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "缺少参数"),
    SC_MISSING_REQUEST_PART(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "缺少请求对象"),
    SC_UNSATISFIED_PARAM(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "参数规则不满足"),
    SC_METHOD_ARGUMENT_NOT_VALID(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "错误请求参数"),
    SC_ACCESS_DENIED(HttpStatus.SC_UNAUTHORIZED, Constants.RT_FAIL, "不允许访问（功能未授权）"),
    SC_FAIL(ApiCodeValue.SC_FAIL, Constants.RT_FAIL, "失败"),
    SC_EMPTY(ApiCodeValue.SC_FAIL, Constants.RT_FAIL, "数据为空"),
    SC_BINDING_ERROR(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "参数绑定错误"),
    SC_PARSING_ERROR(HttpStatus.SC_BAD_REQUEST, Constants.RT_FAIL, "请求格式有误"),

    // --- 5xx Server Error ---
    SC_INTERNAL_SERVER_ERROR(HttpStatus.SC_INTERNAL_SERVER_ERROR, Constants.RT_FAIL, "服务器内部错误，无法完成请求"),
    SC_NOT_IMPLEMENTED(HttpStatus.SC_NOT_IMPLEMENTED, Constants.RT_FAIL, "服务器不支持请求的功能，无法完成请求"),
    SC_BAD_GATEWAY(HttpStatus.SC_BAD_GATEWAY, Constants.RT_FAIL, "错误网关"),
    SC_SERVICE_UNAVAILABLE(HttpStatus.SC_SERVICE_UNAVAILABLE, Constants.RT_FAIL, "服务器目前无法使用（由于超载或停机维护）"),
    SC_GATEWAY_TIMEOUT(HttpStatus.SC_GATEWAY_TIMEOUT, Constants.RT_FAIL, "网关访问超时"),
    SC_HTTP_VERSION_NOT_SUPPORTED(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED, Constants.RT_FAIL, "HTTP 版本不受支持"),
    SC_VARIANT_ALSO_NEGOTIATES(HttpStatus.SC_VARIANT_ALSO_NEGOTIATES, Constants.RT_FAIL, "服务器内部配置错误"),
    SC_INSUFFICIENT_STORAGE(HttpStatus.SC_INSUFFICIENT_STORAGE, Constants.RT_FAIL, "服务器无法存储完成请求所必须的内容"),
    SC_LOOP_DETECTED(HttpStatus.SC_LOOP_DETECTED, Constants.RT_FAIL, "服务器存储空间不足"),
    SC_BANDWIDTH_LIMIT_EXCEEDED(HttpStatus.SC_BANDWIDTH_LIMIT_EXCEEDED, Constants.RT_FAIL, "服务器达到带宽限制"),
    SC_NOT_EXTENDED(HttpStatus.SC_NOT_EXTENDED, Constants.RT_FAIL, "获取资源所需要的策略并没有没满足"),
    SC_NETWORK_AUTHENTICATION_REQUIRED(HttpStatus.SC_NETWORK_AUTHENTICATION_REQUIRED, Constants.RT_FAIL, "要求网络认证");

    private final int code;
    private final String status;
    private final String reason;

    ApiCode(int code, String status, String reason) {
        this.code = code;
        this.status = status;
        this.reason = reason;
    }

    @Override
    public String toString() {
        return String.valueOf(code);
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public String getReason() {
        return reason;
    }

    public <T> ApiRestResponse<T> toResponse() {
        return ApiRestResponse.of(this);
    }

    public <T> ApiRestResponse<T> toResponse(String message) {
        return ApiRestResponse.of(this, message, null);
    }

    public <T> ApiRestResponse<T> toResponse(T data) {
        return ApiRestResponse.of(this, data);
    }

    public <T> ApiRestResponse<T> toResponse(String message, T data) {
        return ApiRestResponse.of(this, message, data);
    }

}
