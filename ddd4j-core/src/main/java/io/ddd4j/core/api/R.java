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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一接口响应，标准的响应数据结构
 *
 * @param <T>
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class R<T> implements IR {

    /**
     *  编码：0/200、请求成功；500、请求成功但服务异常；403、未登录或者token已失效；401、已登录没有权限。
     */
    protected Serializable code;
    /**
     * 返回信息
     */
    protected String msg;
    /**
     * 响应数据
     */
    protected T data;
    /**
     * 校验失败信息（仅在非 null 时序列化）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected List<Map<String, String>> error;

    public R() {
        this(ApiCode.OK.getCode(), ApiCode.OK.getDesc());
    }

    public R(Serializable code, String msg) {
        this(code, msg, null);
    }

    public R(T data) {
        this(ApiCode.OK.getCode(), ApiCode.OK.getDesc(), data);
    }

    public R(Serializable code, String msg, T data) {
        this(code, msg, data, null);
    }

    public R(Serializable code, String msg, T data, List<Map<String, String>> error) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.error = error;
    }

    public static <T> R<T> ok() {
        return new R<>();
    }

    public static <T> R<T> ok(T payload) {
        return new R<>(payload);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R(ApiCode.OK.getCode(), msg, data);
    }

    public static <T> R<T> fail(Serializable code, String msg) {
        return new R(code, msg);
    }

    public static <T> R<T> fail(Serializable code, String msg, T data) {
        return new R(code, msg, data);
    }

    public static <T> R<T> fail() {
        return fail(ApiCode.FAIL.getCode());
    }

    public static <T> R<T> fail(Serializable code) {
        return fail(code, ApiCode.FAIL.getDesc());
    }

    public static <T> R<T> fail(String msg) {
        return fail(ApiCode.FAIL.getCode(), msg);
    }

    // === cloud 兼容别名（failed = fail，isOk 语义对齐 cloud SUCCESS=0） ===

    /**
     * 失败响应（cloud 兼容别名，等价于 {@link #fail()}）。
     */
    public static <T> R<T> failed() {
        return fail();
    }

    /**
     * 失败响应（cloud 兼容别名，等价于 {@link #fail(String)}）。
     */
    public static <T> R<T> failed(String msg) {
        return fail(msg);
    }

    /**
     * 失败响应（cloud 兼容别名，等价于 {@link #fail(T)}）。
     */
    public static <T> R<T> failed(T data) {
        return fail(ApiCode.FAIL.getCode(), ApiCode.FAIL.getDesc(), data);
    }

    /**
     * 失败响应（cloud 兼容别名，等价于 {@link #fail(Serializable, String)}）。
     */
    public static <T> R<T> failed(Serializable code, String msg) {
        return fail(code, msg);
    }

    /**
     * 失败响应（cloud 兼容别名，等价于 {@link #fail(Serializable, String, T)}）。
     */
    public static <T> R<T> failed(T data, String msg) {
        return fail(ApiCode.FAIL.getCode(), msg, data);
    }

    /**
     * 失败响应（cloud 兼容别名，带 data + code + msg）。
     */
    public static <T> R<T> failed(T data, Serializable code, String msg) {
        return fail(code, msg, data);
    }

    // === CustomApiCode 工厂（承接原 ApiRestResponse 能力） ===

    /**
     * 按 {@link CustomApiCode} 构建响应（code 与描述取自定义码）。
     */
    public static <T> R<T> of(CustomApiCode code) {
        return new R(code.getCode(), code.getReason(), null);
    }

    /**
     * 按 {@link CustomApiCode} 构建响应并携带数据。
     */
    public static <T> R<T> of(CustomApiCode code, T data) {
        return new R(code.getCode(), code.getReason(), data);
    }

    // === ApiRestResponse 收编（原 ApiRestResponse 静态工厂/实例方法，合并后唯一入口仍是 R） ===

    // success -----------------------------------------------------------------

    /**
     * 成功响应（code=200），携带自定义消息。
     */
    public static <T> R<T> success(final String message) {
        return new R(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getReason(), null);
    }

    /**
     * 成功响应（code=200），携带数据。
     */
    public static <T> R<T> success(final T data) {
        return new R(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getReason(), data);
    }

    /**
     * 成功响应（按自定义码），携带数据。
     */
    public static <T> R<T> success(final CustomApiCode code, final T data) {
        return of(code, data);
    }

    /**
     * 成功响应（指定 code），携带自定义消息。
     */
    public static <T> R<T> success(final int code, final String message) {
        return new R(code, message, null);
    }

    /**
     * 成功响应（按自定义码），携带自定义消息。
     */
    public static <T> R<T> success(final CustomApiCode code, final String message) {
        return new R(code.getCode(), message, null);
    }

    // fail 补充（fail(String)/fail(Serializable, String) 等 R 原有语义不变） -----

    /**
     * 失败响应（按自定义码），携带数据。
     */
    public static <T> R<T> fail(final CustomApiCode code, final T data) {
        return of(code, data);
    }

    /**
     * 失败响应（按自定义码），携带自定义消息。
     */
    public static <T> R<T> fail(final CustomApiCode code, final String message) {
        return new R(code.getCode(), message, null);
    }

    // error -----------------------------------------------------------------

    /**
     * 错误响应（code=500），携带自定义消息。
     */
    public static <T> R<T> error(final String message) {
        return new R(ApiCode.SERVER_ERROR.getCode(), message, null);
    }

    /**
     * 错误响应（code=500），携带数据。
     */
    public static <T> R<T> error(final T data) {
        return new R(ApiCode.SERVER_ERROR.getCode(), ApiCode.SERVER_ERROR.getReason(), data);
    }

    /**
     * 错误响应（按自定义码），携带数据。
     */
    public static <T> R<T> error(final CustomApiCode code, final T data) {
        return of(code, data);
    }

    /**
     * 错误响应（指定 code），携带自定义消息。
     */
    public static <T> R<T> error(final int code, final String message) {
        return new R(code, message, null);
    }

    /**
     * 错误响应（按自定义码），携带自定义消息。
     */
    public static <T> R<T> error(final CustomApiCode code, final String message) {
        return new R(code.getCode(), message, null);
    }

    /**
     * 错误响应（按自定义码），携带自定义消息与校验失败信息。
     */
    public static <T> R<T> error(final CustomApiCode code, final String message, List<Map<String, String>> error) {
        return new R(code.getCode(), message, null, error);
    }

    // of 补充（of(CustomApiCode) / of(CustomApiCode, T) 见上） ---------------

    /**
     * 按数字 code 构建响应。
     */
    public static <T> R<T> of(final int code, final String message) {
        return new R(code, message, null);
    }

    /**
     * 按字符串 code 构建响应（内部转数字）。
     */
    public static <T> R<T> of(final String code, final String message) {
        return of(Integer.parseInt(code), message);
    }

    /**
     * 按数字 code 构建响应（status 参数为原 ApiRestResponse 兼容占位，R 已无 status 字段）。
     */
    public static <T> R<T> of(final int code, final String status, final String message) {
        return of(code, message);
    }

    /**
     * 按数字 code 构建响应并携带数据（status 参数为兼容占位）。
     */
    public static <T> R<T> of(final int code, final String status, final String message, final T data) {
        return new R(code, message, data);
    }

    public static boolean empty(R<?> r) {
        return Objects.isNull(r) || !Objects.equals(r.getCode(), ApiCode.OK.getCode()) || Objects.isNull(r.getData());
    }

    public static <T> R<T> transform(R<?> source) {
        R<T> target = new R<>();
        target.setCode(source.getCode());
        target.setMsg(source.getMsg());
        return target;
    }

    public Boolean isOk() {
        return Objects.equals(this.getCode(), ApiCode.OK.getCode()) || Objects.equals(this.getCode(), ApiCode.SUCCESS.getCode());
    }

    public Boolean isEmpty() {
        return !isOk() || Objects.isNull(data);
    }

    // === ApiRestResponse 收编（实例方法） ===

    /**
     * 是否成功（原 ApiRestResponse 语义：code == 200）。
     */
    public boolean isSuccess() {
        return Objects.equals(this.getCode(), ApiCode.SUCCESS.getCode());
    }

    /**
     * 原.ApiRestResponse#getMessage 兼容别名，等价于 {@link #getMsg()}。
     * <p>加 {@code @JsonIgnore} 是为了避免 Jackson 把同一字段序列化成 msg/message 两份。
     */
    @JsonIgnore
    public String getMessage() {
        return msg;
    }

    /**
     * 转换为 Map（key：code / msg / data）。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> rtMap = new HashMap<String, Object>();
        rtMap.put("code", code);
        rtMap.put("msg", msg);
        rtMap.put("data", data);
        return rtMap;
    }

}
