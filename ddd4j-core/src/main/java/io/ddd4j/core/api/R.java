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
 * \u7edf\u4e00\u63a5\u53e3\u54cd\u5e94\uff0c\u6807\u51c6\u7684\u54cd\u5e94\u6570\u636e\u7ed3\u6784
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @param <T> \u7531 R \u5904\u7406\u7684\u6cdb\u578b\u7c7b\u578b
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

    /** \u521b\u5efa\u54cd\u5e94\u5bf9\u8c61\u3002 */
    public R() {
        this(ApiCode.OK.getCode(), ApiCode.OK.getDesc());
    }

    /**
     * \u521b\u5efa\u54cd\u5e94\u5bf9\u8c61\u3002
     * @param code \u54cd\u5e94\u7801
     * @param msg \u54cd\u5e94\u6d88\u606f
     */
    public R(Serializable code, String msg) {
        this(code, msg, null);
    }

    /**
     * \u521b\u5efa\u54cd\u5e94\u5bf9\u8c61\u3002
     * @param data \u54cd\u5e94\u6570\u636e
     */
    public R(T data) {
        this(ApiCode.OK.getCode(), ApiCode.OK.getDesc(), data);
    }

    /**
     * \u521b\u5efa\u54cd\u5e94\u5bf9\u8c61\u3002
     * @param code \u54cd\u5e94\u7801
     * @param msg \u54cd\u5e94\u6d88\u606f
     * @param data \u54cd\u5e94\u6570\u636e
     */
    public R(Serializable code, String msg, T data) {
        this(code, msg, data, null);
    }

    /**
     * \u521b\u5efa\u54cd\u5e94\u5bf9\u8c61\u3002
     * @param code \u54cd\u5e94\u7801
     * @param msg \u54cd\u5e94\u6d88\u606f
     * @param data \u54cd\u5e94\u6570\u636e
     * @param error \u6821\u9a8c\u5931\u8d25\u4fe1\u606f
     */
    public R(Serializable code, String msg, T data, List<Map<String, String>> error) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.error = error;
    }

    // ok -----------------------------------------------------------------

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=0\uff09\uff0c\u4e0d\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> ok() {
        return new R<>();
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=0\uff09\uff0c\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param payload \u54cd\u5e94\u6570\u636e
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> ok(T payload) {
        return new R<>(payload);
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=0\uff09\uff0c\u643a\u5e26\u81ea\u5b9a\u4e49\u6d88\u606f\u4e0e\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param msg \u54cd\u5e94\u6d88\u606f
     * @param data \u54cd\u5e94\u6570\u636e
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> ok(String msg, T data) {
        return of(ApiCode.OK.getCode(), msg, data);
    }

    // success -----------------------------------------------------------------

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=200\uff09\uff0c\u4e0d\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> success() {
        return of(ApiCode.SUCCESS);
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=200\uff09\uff0c\u643a\u5e26\u81ea\u5b9a\u4e49\u6d88\u606f\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param message \u54cd\u5e94\u6d88\u606f
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> success(String message) {
        return new R<>(ApiCode.SUCCESS.getCode(), message, null);
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=200\uff09\uff0c\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param data \u54cd\u5e94\u6570\u636e
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> success(T data) {
        return of(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getReason(), data);
    }

    // fail -----------------------------------------------------------------

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u5931\u8d25\uff08code=400\uff09\uff0c\u4e0d\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> fail() {
        return fail(ApiCode.FAIL.getCode());
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u5931\u8d25\uff08code=400\uff09\uff0c\u643a\u5e26\u81ea\u5b9a\u4e49\u6d88\u606f\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> fail(Serializable code) {
        return of(code, ApiCode.FAIL.getDesc());
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u5931\u8d25\uff08code=400\uff09\uff0c\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param message \u54cd\u5e94\u6d88\u606f
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> fail(String message) {
        return of(ApiCode.FAIL.getCode(), message);
    }

    // error -----------------------------------------------------------------

    /**
     * \u9519\u8bef\u54cd\u5e94\uff08code=500\uff09\uff0c\u643a\u5e26\u81ea\u5b9a\u4e49\u6d88\u606f\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param message \u54cd\u5e94\u6d88\u606f
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> error(String message) {
        return new R<>(ApiCode.SERVER_ERROR.getCode(), message, null);
    }

    /**
     * \u9519\u8bef\u54cd\u5e94\uff08code=500\uff09\uff0c\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param data \u54cd\u5e94\u6570\u636e
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> error(T data) {
        return new R<>(ApiCode.SERVER_ERROR.getCode(), ApiCode.SERVER_ERROR.getReason(), data);
    }

    // CustomApiCode -----------------------------------------------------------------

    /**
     * \u6309 {@link CustomApiCode} \u6784\u5efa\u54cd\u5e94\uff08code \u4e0e\u63cf\u8ff0\u53d6\u81ea\u5b9a\u4e49\u7801\uff09\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> of(CustomApiCode code) {
        return new R<>(code.getCode(), code.getReason(), null);
    }

    /**
     * \u6309 {@link CustomApiCode} \u6784\u5efa\u54cd\u5e94\u5e76\u643a\u5e26\u81ea\u5b9a\u4e49\u6d88\u606f\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @param message \u54cd\u5e94\u6d88\u606f
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> of(CustomApiCode code, String message) {
        return new R<>(code.getCode(), message, null);
    }

    /**
     * \u6309 {@link CustomApiCode} \u6784\u5efa\u54cd\u5e94\uff0c\u5e76\u643a\u5e26\u6570\u636e\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @param data \u54cd\u5e94\u6570\u636e
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> of(CustomApiCode code, T data) {
        return new R<>(code.getCode(), code.getReason(), data);
    }

    /**
     * \u6309 {@link CustomApiCode} \u6784\u5efa\u54cd\u5e94\uff0c\u5e76\u643a\u5e26\u81ea\u5b9a\u4e49\u6d88\u606f\u4e0e\u6821\u9a8c\u5931\u8d25\u4fe1\u606f\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @param message \u54cd\u5e94\u6d88\u606f
     * @param error \u6821\u9a8c\u5931\u8d25\u4fe1\u606f
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> of(CustomApiCode code, String message, List<Map<String, String>> error) {
        return new R<>(code.getCode(), message, null, error);
    }

    /**
     * \u6309\u5b57\u7b26\u4e32 code \u6784\u5efa\u54cd\u5e94\uff08\u5185\u90e8\u8f6c\u6570\u5b57\uff09\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @param message \u54cd\u5e94\u6d88\u606f
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> of(String code, String message) {
        return of(Integer.parseInt(code), message);
    }

    /**
     * \u6309\u6570\u5b57 code \u6784\u5efa\u54cd\u5e94\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @param message \u54cd\u5e94\u6d88\u606f
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> of(Serializable code, String message) {
        return new R<>(code, message, null);
    }

    /**
     * \u6309\u6570\u5b57 code \u6784\u5efa\u54cd\u5e94\u5e76\u643a\u5e26\u6570\u636e\uff08status \u53c2\u6570\u4e3a\u517c\u5bb9\u5360\u4f4d\uff09\u3002
     *
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @param message \u54cd\u5e94\u6d88\u606f
     * @param data \u54cd\u5e94\u6570\u636e
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> of(Serializable code, String message, T data) {
        return new R<>(code, message, data);
    }

    /**
     * \u68c0\u67e5\u54cd\u5e94\u662f\u5426\u4e3a\u7a7a\u3002
     * @param r \u5f85\u68c0\u67e5\u54cd\u5e94
     * @return \u54cd\u5e94\u7f3a\u5931\u3001\u5931\u8d25\u6216\u6570\u636e\u4e3a\u7a7a\u65f6\u4e3a true
     */
    public static boolean empty(R<?> r) {
        return Objects.isNull(r) || !Objects.equals(r.getCode(), ApiCode.OK.getCode()) || Objects.isNull(r.getData());
    }

    /**
     * \u590d\u5236\u54cd\u5e94\u7801\u548c\u6d88\u606f\u5230\u65b0\u54cd\u5e94\u3002
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param source \u5f85\u8f6c\u6362\u54cd\u5e94
     * @return \u4fdd\u7559\u54cd\u5e94\u7801\u548c\u6d88\u606f\u7684\u65b0\u54cd\u5e94
     */
    public static <T> R<T> transform(R<?> source) {
        R<T> target = new R<>();
        target.setCode(source.getCode());
        target.setMsg(source.getMsg());
        return target;
    }

    /**
     * \u5224\u65ad\u54cd\u5e94\u7801\u662f\u5426\u8868\u793a\u6210\u529f\u3002
     * @return \u54cd\u5e94\u7801\u8868\u793a\u6210\u529f\u65f6\u4e3a true
     */
    public Boolean isOk() {
        return Objects.equals(this.getCode(), ApiCode.OK.getCode()) || Objects.equals(this.getCode(), ApiCode.SUCCESS.getCode());
    }

    /**
     * \u5224\u65ad\u54cd\u5e94\u662f\u5426\u5931\u8d25\u6216\u65e0\u6570\u636e\u3002
     * @return \u54cd\u5e94\u5931\u8d25\u6216\u6570\u636e\u4e3a\u7a7a\u65f6\u4e3a true
     */
    public Boolean isEmpty() {
        return !isOk() || Objects.isNull(data);
    }

    // === ApiRestResponse 收编（实例方法） ===

    /**
     * \u662f\u5426\u6210\u529f\uff08\u539f ApiRestResponse \u8bed\u4e49\uff1acode == 200\uff09\u3002
     *
     * @return \u54cd\u5e94\u7801\u4e3a\u6210\u529f\u7801\u65f6\u4e3a true
     */
    public boolean isSuccess() {
        return Objects.equals(this.getCode(), ApiCode.SUCCESS.getCode());
    }

    /**
     * \u539f.ApiRestResponse#getMessage \u517c\u5bb9\u522b\u540d\uff0c\u7b49\u4ef7\u4e8e {@link #getMsg()}\u3002
     *  <p>\u52a0 {@code @JsonIgnore} \u662f\u4e3a\u4e86\u907f\u514d Jackson \u628a\u540c\u4e00\u5b57\u6bb5\u5e8f\u5217\u5316\u6210 msg/message \u4e24\u4efd\u3002
     *
     * @return \u54cd\u5e94\u6d88\u606f
     */
    @JsonIgnore
    public String getMessage() {
        return msg;
    }

    /**
     * \u8f6c\u6362\u4e3a Map\uff08key\uff1acode / msg / data\uff09\u3002
     *
     * @return \u5305\u542b code\u3001msg\u3001data \u7684\u6620\u5c04
     */
    public Map<String, Object> toMap() {
        Map<String, Object> rtMap = new HashMap<String, Object>();
        rtMap.put("code", code);
        rtMap.put("msg", msg);
        rtMap.put("data", data);
        return rtMap;
    }

}
