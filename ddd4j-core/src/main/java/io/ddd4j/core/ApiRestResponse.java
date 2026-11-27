/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.ddd4j.core.constant.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * model for interacting with client.
 */
@Schema(name = "ApiRestResponse", description = "接口响应对象")
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiRestResponse<T> {

    /** 调用方收到的业务状态编码。 */
    @Schema(name = "code", type = "integer", description = "成功或异常编码")
    @Getter
    private final int code;

    /** 返回给调用方的结果说明。 */
    @Schema(name = "message", type = "string", description = "成功或异常消息")
    @Getter
    private final String message;

    /** 响应体携带的业务数据。 */
    @Schema(name = "data", description = "成功或异常数据")
    @Getter
    private T data;

    /** 按字段描述的输入校验错误集合。 */
    @Schema(name = "error", description = "校验失败信息")
    private List<Map<String, String>> error;

    /** 初始化状态编码和说明，并装入此重载接收的数据或校验错误。 */
    public ApiRestResponse() {
        this.code = ApiCode.SC_SUCCESS.getCode();
        this.message = ApiCode.SC_SUCCESS.getReason();
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     */
    protected ApiRestResponse(final ApiCode code) {
        this.code = code.getCode();
        this.message = code.getReason();
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     * @param data 随响应携带的业务数据
     */
    protected ApiRestResponse(final ApiCode code, final T data) {
        this.code = code.getCode();
        this.message = code.getReason();
        this.data = data;
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     */
    protected ApiRestResponse(final CustomApiCode code) {
        this.code = code.getCode();
        this.message = code.getReason();
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     * @param data 随响应携带的业务数据
     */
    protected ApiRestResponse(final CustomApiCode code, final T data) {
        this.code = code.getCode();
        this.message = code.getReason();
        this.data = data;
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @param data 随响应携带的业务数据
     */
    protected ApiRestResponse(final ApiCode code, final String message, final T data) {
        this.code = code.getCode();
        this.message = message;
        this.data = data;
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @param data 随响应携带的业务数据
     * @param error 按字段描述的校验失败信息
     */
    protected ApiRestResponse(final ApiCode code, final String message, final T data, List<Map<String, String>> error) {
        this.code = code.getCode();
        this.message = message;
        this.data = data;
        this.error = error;
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     */
    protected ApiRestResponse(final int code, final String message) {
        this(code, Constants.RT_SUCCESS, message);
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     * @param status 为兼容既有调用保留的状态参数
     * @param message 随响应返回的业务说明
     */
    protected ApiRestResponse(final int code, final String status, final String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @param data 随响应携带的业务数据
     */
    protected ApiRestResponse(final int code, final String message, final T data) {
        this(code, Constants.RT_SUCCESS, message, data);
    }

    /**
     * 初始化状态编码和说明，并装入此重载接收的数据或校验错误。
     *
     * @param code 随响应返回的业务状态编码
     * @param status 为兼容既有调用保留的状态参数
     * @param message 随响应返回的业务说明
     * @param data 随响应携带的业务数据
     */
    protected ApiRestResponse(final int code, final String status, final String message, final T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // success -----------------------------------------------------------------

    /**
     * 构造对客户端表示成功的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param message 随响应返回的业务说明
     * @return 携带成功结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> success(final String message) {
        return of(ApiCode.SC_SUCCESS, message, null);
    }

    /**
     * 构造对客户端表示成功的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param data 随响应携带的业务数据
     * @return 携带成功结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> success(final T data) {
        return of(ApiCode.SC_SUCCESS, data);
    }

    /**
     * 构造对客户端表示成功的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param data 随响应携带的业务数据
     * @return 携带成功结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> success(final ApiCode code, final T data) {
        return of(code, data);
    }

    /**
     * 构造对客户端表示成功的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @return 携带成功结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> success(final int code, final String message) {
        return of(code, Constants.RT_SUCCESS, message);
    }

    /**
     * 构造对客户端表示成功的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @return 携带成功结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> success(final ApiCode code, final String message) {
        return of(code, message, null);
    }

    // fail -----------------------------------------------------------------

    /**
     * 构造对客户端表示失败的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param message 随响应返回的业务说明
     * @return 携带失败结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> fail(final String message) {
        return of(ApiCode.SC_FAIL, message, null);
    }

    /**
     * 构造对客户端表示失败的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param data 随响应携带的业务数据
     * @return 携带失败结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> fail(final T data) {
        return of(ApiCode.SC_FAIL, data);
    }

    /**
     * 构造对客户端表示失败的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param data 随响应携带的业务数据
     * @return 携带失败结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> fail(final ApiCode code, final T data) {
        return of(code, data);
    }

    /**
     * 构造对客户端表示失败的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @return 携带失败结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> fail(final int code, final String message) {
        return of(code, Constants.RT_FAIL, message);
    }

    /**
     * 构造对客户端表示失败的响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @return 携带失败结果的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> fail(final ApiCode code, final String message) {
        return of(code, message, null);
    }

    // error -----------------------------------------------------------------

    /**
     * 按 <code>error</code> 的契约计算并返回结果。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param message 随响应返回的业务说明
     * @return 返回表达式 <code>of(ApiCode.SC_INTERNAL_SERVER_ERROR, message, null)</code>
     */
    public static <T> ApiRestResponse<T> error(final String message) {
        return of(ApiCode.SC_INTERNAL_SERVER_ERROR, message, null);
    }

    /**
     * 按 <code>error</code> 的契约计算并返回结果。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param data 随响应携带的业务数据
     * @return 返回表达式 <code>of(ApiCode.SC_INTERNAL_SERVER_ERROR, data)</code>
     */
    public static <T> ApiRestResponse<T> error(final T data) {
        return of(ApiCode.SC_INTERNAL_SERVER_ERROR, data);
    }

    /**
     * 按 <code>error</code> 的契约计算并返回结果。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param data 随响应携带的业务数据
     * @return 返回表达式 <code>of(code, data)</code>
     */
    public static <T> ApiRestResponse<T> error(final ApiCode code, final T data) {
        return of(code, data);
    }

    /**
     * 按 <code>error</code> 的契约计算并返回结果。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @return 返回表达式 <code>of(code, Constants.RT_ERROR, message)</code>
     */
    public static <T> ApiRestResponse<T> error(final int code, final String message) {
        return of(code, Constants.RT_ERROR, message);
    }

    /**
     * 按 <code>error</code> 的契约计算并返回结果。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @return 返回表达式 <code>of(code, message, null)</code>
     */
    public static <T> ApiRestResponse<T> error(final ApiCode code, final String message) {
        return of(code, message, null);
    }

    /**
     * 按 <code>error</code> 的契约计算并返回结果。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @param error 按字段描述的校验失败信息
     * @return 返回表达式 <code>of(code, message, null, error)</code>
     */
    public static <T> ApiRestResponse<T> error(final ApiCode code, final String message, List<Map<String, String>> error) {
        return of(code, message, null, error);
    }

    // -----------------------------------------------------------------

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final ApiCode code) {
        return new ApiRestResponse<T>(code);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param data 随响应携带的业务数据
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final ApiCode code, final T data) {
        return new ApiRestResponse<T>(code, data);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @param data 随响应携带的业务数据
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final ApiCode code, final String message, final T data) {
        return new ApiRestResponse<T>(code, message, data);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @param data 随响应携带的业务数据
     * @param error 按字段描述的校验失败信息
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final ApiCode code, final String message, final T data, List<Map<String, String>> error) {
        return new ApiRestResponse<T>(code, message, data, error);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final String code, final String message) {
        return new ApiRestResponse<T>(Integer.parseInt(code), message);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param message 随响应返回的业务说明
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final int code, final String message) {
        return new ApiRestResponse<T>(code, message);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param status 为兼容既有调用保留的状态参数
     * @param message 随响应返回的业务说明
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final String code, final String status, final String message) {
        return of(Integer.parseInt(code), status, message, null);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param status 为兼容既有调用保留的状态参数
     * @param message 随响应返回的业务说明
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final int code, final String status, final String message) {
        return of(code, status, message, null);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param status 为兼容既有调用保留的状态参数
     * @param message 随响应返回的业务说明
     * @param data 随响应携带的业务数据
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final int code, final String status, final String message, final T data) {
        return new ApiRestResponse<T>(code, status, message, data);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final CustomApiCode code) {
        return new ApiRestResponse<T>(code);
    }

    /**
     * 按给定状态编码、说明和数据组装客户端响应。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param data 随响应携带的业务数据
     * @return 组装完成的 HTTP 响应
     */
    public static <T> ApiRestResponse<T> of(final CustomApiCode code, final T data) {
        return new ApiRestResponse<T>(code, data);
    }

    /**
     * 返回响应携带的字段级校验信息。
     *
     * @return 返回表达式 <code>error</code>
     */
    @JsonIgnore
    public List<Map<String, String>> getError() {
        return error;
    }

    /**
     * 按 <code>is Success</code> 的契约计算并返回结果。
     *
     * @return 返回表达式 <code>code == ApiCodeValue.SC_SUCCESS</code>
     */
    @JsonIgnore
    public boolean isSuccess() {
        return code == ApiCodeValue.SC_SUCCESS;
    }

    /**
     * 读取<code>rtMap</code> 的当前值。
     *
     * @return 返回表达式 <code>rtMap</code>
     */
    public Map<String, Object> toMap() {
        Map<String, Object> rtMap = new HashMap<String, Object>();
        rtMap.put("code", code);
        rtMap.put("msg", message);
        rtMap.put("data", data);
        return rtMap;
    }

}
