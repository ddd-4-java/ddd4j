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
 * @param <T> 响应数据类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class R<T> implements IR {

    /**
     * 编码：0/200、请求成功；500、请求成功但服务异常；403、未登录或者token已失效；401、已登录没有权限。
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

    /**
     * 创建响应对象。
     */
    public R() {
        this(ApiCode.OK.getCode(), ApiCode.OK.getDesc());
    }

    /**
     * 创建响应对象。
     *
     * @param code 响应码
     * @param msg 响应消息
     */
    public R(Serializable code, String msg) {
        this(code, msg, null);
    }

    /**
     * 创建响应对象。
     *
     * @param data 响应数据
     */
    public R(T data) {
        this(ApiCode.OK.getCode(), ApiCode.OK.getDesc(), data);
    }

    /**
     * 创建响应对象。
     *
     * @param code 响应码
     * @param msg 响应消息
     * @param data 响应数据
     */
    public R(Serializable code, String msg, T data) {
        this(code, msg, data, null);
    }

    /**
     * 创建响应对象。
     *
     * @param code 响应码
     * @param msg 响应消息
     * @param data 响应数据
     * @param error 校验失败信息
     */
    public R(Serializable code, String msg, T data, List<Map<String, String>> error) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.error = error;
    }

    // ok -----------------------------------------------------------------

    /**
     * 请求/操作成功（code=0），不携带数据。
     * @param <T> 响应数据类型
     * @return 响应对象
     */
    public static <T> R<T> ok() {
        return new R<>();
    }

    /**
     * 请求/操作成功（code=0），携带数据。
     * @param <T> 响应数据类型
     * @param payload 响应数据
     * @return 响应对象
     */
    public static <T> R<T> ok(T payload) {
        return new R<>(payload);
    }

    /**
     * 请求/操作成功（code=0），携带自定义消息与数据。
     * @param <T> 响应数据类型
     * @param msg 响应消息
     * @param data 响应数据
     * @return 响应对象
     */
    public static <T> R<T> ok(String msg, T data) {
        return of(ApiCode.OK.getCode(), msg, data);
    }

    // success -----------------------------------------------------------------

    /**
     * 请求/操作成功（code=200），不携带数据。
     * @param <T> 响应数据类型
     * @return 响应对象
     */
    public static <T> R<T> success() {
        return of(ApiCode.SUCCESS);
    }

    /**
     * 请求/操作成功（code=200），携带自定义消息。
     * @param <T> 响应数据类型
     * @param message 响应消息
     * @return 响应对象
     */
    public static <T> R<T> success(String message) {
        return new R<>(ApiCode.SUCCESS.getCode(), message, null);
    }

    /**
     * 请求/操作成功（code=200），携带数据。
     * @param <T> 响应数据类型
     * @param data 响应数据
     * @return 响应对象
     */
    public static <T> R<T> success(T data) {
        return of(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getReason(), data);
    }

    // fail -----------------------------------------------------------------

    /**
     * 请求/操作失败（code=400），不携带数据。
     * @param <T> 响应数据类型
     * @return 响应对象
     */
    public static <T> R<T> fail() {
        return fail(ApiCode.FAIL.getCode());
    }

    /**
     * 请求/操作失败，使用指定响应码和默认失败消息。
     * @param <T> 响应数据类型
     * @param code 响应码
     * @return 响应对象
     */
    public static <T> R<T> fail(Serializable code) {
        return of(code, ApiCode.FAIL.getDesc());
    }

    /**
     * 请求/操作失败（code=400），携带自定义消息。
     * @param <T> 响应数据类型
     * @param message 响应消息
     * @return 响应对象
     */
    public static <T> R<T> fail(String message) {
        return of(ApiCode.FAIL.getCode(), message);
    }

    // error -----------------------------------------------------------------

    /**
     * 错误响应（code=500），携带自定义消息。
     * @param <T> 响应数据类型
     * @param message 响应消息
     * @return 响应对象
     */
    public static <T> R<T> error(String message) {
        return new R<>(ApiCode.SERVER_ERROR.getCode(), message, null);
    }

    /**
     * 错误响应（code=500），携带数据。
     * @param <T> 响应数据类型
     * @param data 响应数据
     * @return 响应对象
     */
    public static <T> R<T> error(T data) {
        return new R<>(ApiCode.SERVER_ERROR.getCode(), ApiCode.SERVER_ERROR.getReason(), data);
    }

    // CustomApiCode -----------------------------------------------------------------

    /**
     * 按 {@link CustomApiCode} 构建响应（code 与描述取自定义码）。
     * @param <T> 响应数据类型
     * @param code 响应码
     * @return 响应对象
     */
    public static <T> R<T> of(CustomApiCode code) {
        return new R<>(code.getCode(), code.getReason(), null);
    }

    /**
     * 按 {@link CustomApiCode} 构建响应并携带自定义消息。
     * @param <T> 响应数据类型
     * @param code 响应码
     * @param message 响应消息
     * @return 响应对象
     */
    public static <T> R<T> of(CustomApiCode code, String message) {
        return new R<>(code.getCode(), message, null);
    }

    /**
     * 按 {@link CustomApiCode} 构建响应，并携带数据。
     * @param <T> 响应数据类型
     * @param code 响应码
     * @param data 响应数据
     * @return 响应对象
     */
    public static <T> R<T> of(CustomApiCode code, T data) {
        return new R<>(code.getCode(), code.getReason(), data);
    }

    /**
     * 按 {@link CustomApiCode} 构建响应，并携带自定义消息与校验失败信息。
     * @param <T> 响应数据类型
     * @param code 响应码
     * @param message 响应消息
     * @param error 校验失败信息
     * @return 响应对象
     */
    public static <T> R<T> of(CustomApiCode code, String message, List<Map<String, String>> error) {
        return new R<>(code.getCode(), message, null, error);
    }

    /**
     * 按字符串 code 构建响应（内部转数字）。
     * @param <T> 响应数据类型
     * @param code 响应码
     * @param message 响应消息
     * @return 响应对象
     */
    public static <T> R<T> of(String code, String message) {
        return of(Integer.parseInt(code), message);
    }

    /**
     * 按数字 code 构建响应。
     * @param <T> 响应数据类型
     * @param code 响应码
     * @param message 响应消息
     * @return 响应对象
     */
    public static <T> R<T> of(Serializable code, String message) {
        return new R<>(code, message, null);
    }

    /**
     * 按指定响应码和消息构建带数据的响应。
     * @param <T> 响应数据类型
     * @param code 响应码
     * @param message 响应消息
     * @param data 响应数据
     * @return 响应对象
     */
    public static <T> R<T> of(Serializable code, String message, T data) {
        return new R<>(code, message, data);
    }

    /**
     * 检查响应是否为空。
     *
     * @param r 待检查响应
     * @return 响应缺失、失败或数据为空时为 true
     */
    public static boolean empty(R<?> r) {
        return Objects.isNull(r) || !Objects.equals(r.getCode(), ApiCode.OK.getCode()) || Objects.isNull(r.getData());
    }

    /**
     * 复制响应码和消息到新响应。
     *
     * @param <T> 响应数据类型
     * @param source 待转换响应
     * @return 保留响应码和消息的新响应
     */
    public static <T> R<T> transform(R<?> source) {
        R<T> target = new R<>();
        target.setCode(source.getCode());
        target.setMsg(source.getMsg());
        return target;
    }

    /**
     * 判断响应码是否表示成功。
     *
     * @return 响应码表示成功时为 true
     */
    public Boolean isOk() {
        return Objects.equals(this.getCode(), ApiCode.OK.getCode()) || Objects.equals(this.getCode(), ApiCode.SUCCESS.getCode());
    }

    /**
     * 判断响应是否失败或无数据。
     *
     * @return 响应失败或数据为空时为 true
     */
    public Boolean isEmpty() {
        return !isOk() || Objects.isNull(data);
    }

    // === ApiRestResponse 收编（实例方法） ===

    /**
     * 是否成功（原 ApiRestResponse 语义：code == 200）。
     * @return 响应码为成功码时为 true
     */
    public boolean isSuccess() {
        return Objects.equals(this.getCode(), ApiCode.SUCCESS.getCode());
    }

    /**
     * 原.ApiRestResponse#getMessage 兼容别名，等价于 {@link #getMsg()}。
     * <p>加 {@code @JsonIgnore} 是为了避免 Jackson 把同一字段序列化成 msg/message 两份。
     * @return 响应消息
     */
    @JsonIgnore
    public String getMessage() {
        return msg;
    }

    /**
     * 转换为 Map（key：code / msg / data）。
     * @return 包含 code、msg、data 的映射
     */
    public Map<String, Object> toMap() {
        Map<String, Object> rtMap = new HashMap<String, Object>();
        rtMap.put("code", code);
        rtMap.put("msg", msg);
        rtMap.put("data", data);
        return rtMap;
    }

}
