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
package io.ddd4j.core.api;

import io.ddd4j.core.constant.HttpStatus;
import io.ddd4j.core.enums.IEnum;
import lombok.Getter;

/**
 * 统一错误码目录。
 * <p>业务自定义错误码请实现 {@link CustomApiCode}（实现即获得 {@link #toResponse()} 系列统一响应构建能力）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public enum ApiCode implements IEnum<Integer>, CustomApiCode {

    /**
     * 请求/操作成功（0）
     */
    OK(0, "请求/操作成功"),
    /**
     * 请求/操作成功（200，HTTP 标准状态码）
     */
    SUCCESS(200, "请求/操作成功"),
    /**
     * 请求成功但服务异常
     */
    FAIL(1, "请求成功但是服务异常"),
    /**
     * 数据为空
     */
    EMPTY(1000, "数据为空"),

    // --- 4xx Client Error ---

    // HTTP Status 4xx(客户端错误，请求包含语法错误或无法完成请求) →这些状态代码表示请求可能出错，妨碍了服务器的处理。
    /**
     * 请求异常（400）
     */
    BAD_REQUEST(HttpStatus.SC_BAD_REQUEST, "请求异常"),
    /**
     * 未登录或 token 已经失效（401）
     */
    UNAUTHORIZED(HttpStatus.SC_UNAUTHORIZED, "未登录或token已经失效"),
    /**
     * 没有权限（403）
     */
    FORBIDDEN(HttpStatus.SC_FORBIDDEN, "没有权限"),
    /**
     * HTTP Status 404（未找到） → 服务器找不到请求的资源（网页）。通过此代码，网站设计人员可设置"您所请求的资源无法找到"的个性页面
     * NoSuchRequestHandlingMethodException 404 (Not Found) NoHandlerFoundException
     * 404 (Not Found)
     */
    NOT_FOUND(HttpStatus.SC_NOT_FOUND, "请求的资源或接口不存在"),
    /**
     * HTTP Status 405（方法禁用） →禁用请求中指定的方法。 HttpRequestMethodNotSupportedException
     * 405 (Method Not Allowed)
     */
    METHOD_NOT_ALLOWED(HttpStatus.SC_METHOD_NOT_ALLOWED, "客户端请求中的方法被禁止"),
    /**
     * HTTP Status 406（不接受） →服务器无法根据客户端请求的内容特性完成请求。
     * HttpMediaTypeNotAcceptableException 406 (Not Acceptable)
     */
    NOT_ACCEPTABLE(HttpStatus.SC_NOT_ACCEPTABLE, "服务器无法根据客户端请求的内容特性完成请求"),
    /**
     * HTTP Status 407（需要代理授权） →此状态代码与 401（未授权）类似，但指定请求者应当授权使用代理。
     */
    PROXY_AUTHENTICATION_REQUIRED(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, "要求进行代理身份验证"),
    /**
     * HTTP Status 408（请求超时） →服务器等待客户端发送的请求时间过长，超时。
     */
    REQUEST_TIMEOUT(HttpStatus.SC_REQUEST_TIMEOUT, "服务器等候请求时发生超时"),
    /**
     * HTTP Status 409（冲突） →服务器在完成请求时发生冲突。 服务器必须在响应中包含有关冲突的信息。
     */
    CONFLICT(HttpStatus.SC_CONFLICT, "服务器找不到请求的地址"),
    /**
     * HTTP Status 410（已删除） → 如果请求的资源已永久删除，服务器就会返回此响应。
     */
    GONE(HttpStatus.SC_GONE, "服务器找不到请求的地址"),
    /**
     * HTTP Status 411（需要有效长度） →服务器无法处理客户端发送的不带Content-Length的请求信息。
     */
    LENGTH_REQUIRED(HttpStatus.SC_LENGTH_REQUIRED, "服务器拒绝接受不带Content-Length请求头的客户端请求"),
    /**
     * HTTP Status 412（未满足前提条件） →服务器未满足请求者在请求中设置的其中一个前提条件。
     */
    PRECONDITION_FAILED(HttpStatus.SC_PRECONDITION_FAILED, "客户端请求信息的先决条件错误"),
    /**
     * HTTP Status 413（请求实体过大）
     * →服务器无法处理请求，因为请求实体过大，超出服务器的处理能力。为防止客户端的连续请求，服务器可能会关闭连接。如果只是服务器暂时无法处理，则会包含一个Retry-After的响应信息。
     */
    REQUEST_TOO_LONG(HttpStatus.SC_REQUEST_TOO_LONG, "服务器无法处理请求，因为请求实体过大，超出服务器的处理能力"),
    /**
     * HTTP Status 415（不支持的媒体类型） →请求的格式不受请求页面的支持。
     * HttpMediaTypeNotSupportedException 415 (Unsupported Media Type)
     */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "不支持的 Content-Type 类型"),
    /**
     * HTTP Status 416（请求范围不符合要求） →如果页面无法提供请求的范围，则服务器会返回此状态代码。
     */
    REQUESTED_RANGE_NOT_SATISFIABLE(HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE, "客户端请求的范围无效"),
    /**
     * HTTP Status 417（未满足期望值） →服务器未满足”Expect”请求标头字段的要求。
     */
    EXPECTATION_FAILED(HttpStatus.SC_EXPECTATION_FAILED, "服务器无法满足Expect的请求头信息"),
    /**
     * HTTP Status 422（无法处理的请求实体） →请求格式正确，但是由于含有语义错误，无法响应。
     */
    UNPROCESSABLE_ENTITY(HttpStatus.SC_UNPROCESSABLE_ENTITY, "无法处理的请求实体"),
    /**
     * HTTP Status 423（当前资源被锁定）
     */
    LOCKED(HttpStatus.SC_LOCKED, "当前资源被锁定 "),
    /**
     * HTTP Status 424（依赖导致的失败）→由于之前的某个请求发生的错误，导致当前请求失败，例如 PROPPATCH。
     */
    FAILED_DEPENDENCY(HttpStatus.SC_FAILED_DEPENDENCY, "依赖导致的失败"),
    /**
     * HTTP Status 426（客户端应当切换到TLS/1.0）
     */
    UPGRADE_REQUIRED(HttpStatus.SC_UPGRADE_REQUIRED, "客户端应当切换到TLS/1.0"),
    /**
     * HTTP Status 428（要求先决条件） → 先决条件是客户端发送 HTTP 请求时，如果想要请求能成功必须满足一些预设的条件。
     */
    PRECONDITION_REQUIRED(HttpStatus.SC_PRECONDITION_REQUIRED, "要求先决条件"),
    /**
     * HTTP Status 429（太多请求） → 当你需要限制客户端请求某个服务数量时，该状态码就很有用，也就是请求速度限制。
     */
    TOO_MANY_REQUESTS(HttpStatus.SC_TOO_MANY_REQUESTS, "太多请求"),
    /**
     * HTTP Status 431（请求头字段太大） → 某些情况下，客户端发送 HTTP 请求头会变得很大，那么服务器可发送 431 Request
     * Header Fields Too Large 来指明该问题。
     */
    REQUEST_HEADER_FIELDS_TOO_LARGE(HttpStatus.SC_REQUEST_HEADER_FIELDS_TOO_LARGE, "请求头字段太大"),
    /**
     * HTTP Status 451（因法律原因不可用） →
     */
    UNAVAILABLE_FOR_LEGAL_REASONS(HttpStatus.SC_UNAVAILABLE_FOR_LEGAL_REASONS, "该请求因法律原因不可用"),

    // --- Custom 4xx Client Error（Spring MVC 异常映射，共享 400/401 码值） ---

    /**
     * TypeMismatchException 400 (Bad Request)
     */
    TYPE_MISMATCH(HttpStatus.SC_BAD_REQUEST, "参数类型不匹配"),
    /**
     * MissingMatrixVariableException 400 (Bad Request)
     */
    MISSING_MATRIX_VARIABLE(HttpStatus.SC_BAD_REQUEST, "缺少矩阵变量"),
    /**
     * MissingPathVariableException 400 (Bad Request)
     */
    MISSING_PATH_VARIABLE(HttpStatus.SC_BAD_REQUEST, "缺少URI模板变量"),
    /**
     * MissingRequestCookieException 400 (Bad Request)
     */
    MISSING_REQUEST_COOKIE(HttpStatus.SC_BAD_REQUEST, "缺少Cookie变量"),
    /**
     * MissingRequestHeaderException 400 (Bad Request)
     */
    MISSING_REQUEST_HEADER(HttpStatus.SC_BAD_REQUEST, "缺少请求头"),
    /**
     * MissingServletRequestParameterException 400 (Bad Request)
     */
    MISSING_REQUEST_PARAM(HttpStatus.SC_BAD_REQUEST, "缺少参数"),
    /**
     * MissingServletRequestPartException 400 (Bad Request)
     */
    MISSING_REQUEST_PART(HttpStatus.SC_BAD_REQUEST, "缺少请求对象"),
    /**
     * UnsatisfiedServletRequestParameterException 400 (Bad Request)
     */
    UNSATISFIED_PARAM(HttpStatus.SC_BAD_REQUEST, "参数规则不满足"),
    /**
     * MethodArgumentNotValidException 400 (Bad Request) BindException 400 (Bad
     * Request)
     */
    METHOD_ARGUMENT_NOT_VALID(HttpStatus.SC_BAD_REQUEST, "错误请求参数"),

    ACCESS_DENIED(HttpStatus.SC_UNAUTHORIZED, "不允许访问（功能未授权）"),

    /**
     * ServletRequestBindingException 400 (Bad Request)
     */
    BINDING_ERROR(HttpStatus.SC_BAD_REQUEST, "参数绑定错误"),
    /**
     * JacksonException 400 (Bad Request) HttpMessageNotReadableException 400
     * (Bad Request)
     */
    PARSING_ERROR(HttpStatus.SC_BAD_REQUEST, "请求格式有误"),

    // --- 5xx Server Error ---

    // HTTP Status 5xx（服务器错误，服务器在处理请求的过程中发生了错误） → 这些状态代码表示服务器在尝试处理请求时发生内部错误。
    // 这些错误可能是服务器本身的错误，而不是请求出错。

    /**
     * 服务器异常（500）
     */
    SERVER_ERROR(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务器异常"),

    /**
     * HTTP Status 501（尚未实施） →服务器不具备完成请求的功能。 例如，服务器无法识别请求方法时可能会返回此代码。
     */
    NOT_IMPLEMENTED(HttpStatus.SC_NOT_IMPLEMENTED, "服务器不支持请求的功能，无法完成请求"),
    /**
     * HTTP Status 502（错误网关） → 作为网关或者代理工作的服务器尝试执行请求时，从上游服务器接收到无效的响应。
     */
    BAD_GATEWAY(HttpStatus.SC_BAD_GATEWAY, "错误网关"),
    /**
     * HTTP Status 503（服务不可用） →
     * 由于临时的服务器维护或者过载，服务器当前无法处理请求。这个状况是临时的，并且将在一段时间以后恢复。如果能够预计延迟时间，那么响应中可以包含一个
     * Retry-After头用以标明该问题。如果没有给出这个 Retry-After 信息，那么客户端会以处理500响应的方式处理它。
     */
    SERVICE_UNAVAILABLE(HttpStatus.SC_SERVICE_UNAVAILABLE, "服务器目前无法使用（由于超载或停机维护）"),
    /**
     * HTTP Status 504（网关访问超时） →
     * 作为网关或者代理服务器尝试执行请求时，未能及时从上游服务器（URI标识的服务器，例如HTTP、FTP、LDAP）或者辅助服务器（例如DNS）收到响应。
     */
    GATEWAY_TIMEOUT(HttpStatus.SC_GATEWAY_TIMEOUT, "网关访问超时"),
    /**
     * HTTP Status 505（HTTP 版本不受支持） →
     * 服务器不支持请求的HTTP协议的版本，无法完成处理。
     */
    HTTP_VERSION_NOT_SUPPORTED(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED, "HTTP 版本不受支持"),
    /**
     * HTTP Status 506（服务器内部配置错误）→ 由《透明内容协商协议》（RFC 2295）扩展，代表服务器存在内部配置错误。
     */
    VARIANT_ALSO_NEGOTIATES(HttpStatus.SC_VARIANT_ALSO_NEGOTIATES, "服务器内部配置错误"),
    /**
     * HTTP Status 507（服务器无法存储完成请求所必须的内容）→ 这个状况被认为是临时的。WebDAV (RFC 4918)
     */
    INSUFFICIENT_STORAGE(HttpStatus.SC_INSUFFICIENT_STORAGE, "服务器无法存储完成请求所必须的内容"),
    /**
     * HTTP Status 508（存储空间不足）
     */
    LOOP_DETECTED(HttpStatus.SC_LOOP_DETECTED, "服务器存储空间不足"),
    /**
     * HTTP Status 509（服务器达到带宽限制）→这不是一个官方的状态码，但是仍被广泛使用。
     */
    BANDWIDTH_LIMIT_EXCEEDED(HttpStatus.SC_BANDWIDTH_LIMIT_EXCEEDED, "服务器达到带宽限制"),
    /**
     * HTTP Status 510（获取资源所需要的策略并没有没满足）
     */
    NOT_EXTENDED(HttpStatus.SC_NOT_EXTENDED, "获取资源所需要的策略并没有没满足"),
    /**
     * HTTP Status 511（要求网络认证）
     */
    NETWORK_AUTHENTICATION_REQUIRED(HttpStatus.SC_NETWORK_AUTHENTICATION_REQUIRED, "要求网络认证");

    private final Integer code;
    private final String desc;

    private ApiCode(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取错误原因描述（{@link CustomApiCode} 契约方法，等价于 {@link #getDesc()}）。
     *
     * @return 错误原因
     */
    public String getReason() {
        return desc;
    }

    /**
     * 根据错误码获取对应的错误描述。
     *
     * @param code 错误码
     * @return 错误描述，未找到返回空字符串
     */
    public static String getDescByCode(Integer code) {
        String desc = "";
        for (ApiCode codeEnum : values()) {
            if (codeEnum.getCode().equals(code)) {
                desc = codeEnum.getDesc();
                break;
            }
        }
        return desc;
    }

    /**
     * 根据错误码获取对应的枚举实例。
     * <p>HTTP 异常映射码段存在共享码值（如 400 段的异常映射条目），共享码值返回声明在前的第一个条目。
     *
     * @param code 错误码
     * @return 对应的 ResultCode 枚举，未找到返回 null
     */
    public static ApiCode getByCode(Integer code) {
        ApiCode resultCode = null;
        for (ApiCode codeEnum : values()) {
            if (codeEnum.getCode().equals(code)) {
                resultCode = codeEnum;
                break;
            }
        }
        return resultCode;
    }
}
