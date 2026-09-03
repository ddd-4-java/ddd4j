package io.ddd4j.core.contract;

import io.ddd4j.core.contract.enums.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * 统一接口响应，标准的响应数据结构
 *
 * @param <T>
 * @author Jensen
 * @公众号 架构师修行录
 */
@Data
@AllArgsConstructor

/**
 * @deprecated 1.0.x 契约归位：与 3.0.x 对齐后，本类语义已由 {@code io.ddd4j.core.api.R} 承接。
 * 本类仅为保持 1.0.x 现有消费方兼容而保留，新代码请使用新包路径。
 */
@Deprecated
public class R<T> implements IR {
    // 编码：0/200、请求成功；500、请求成功但服务异常；403、未登录或者token已失效；401、已登录没有权限。
    protected Serializable code;
    // 返回信息
    protected String msg;
    // 响应数据
    protected T data;

    public Boolean isOk() {
        return Objects.equals(this.getCode(), ResultCode.OK.getCode()) || Objects.equals(this.getCode(), ResultCode.SUCCESS.getCode());
    }

    public Boolean isEmpty() {
        return !isOk() || data == null;
    }

    public R() {
        this(ResultCode.OK.getCode(), ResultCode.OK.getDesc());
    }

    public R(Serializable code, String msg) {
        this(code, msg, null);
    }

    public R(T data) {
        this(ResultCode.OK.getCode(), ResultCode.OK.getDesc(), data);
    }

    public static <T> R<T> ok() {
        return new R<>();
    }

    public static <T> R<T> ok(T payload) {
        return new R<>(payload);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R(ResultCode.OK.getCode(), msg, data);
    }

    public static <T> R<T> fail(Serializable code, String msg) {
        return new R(code, msg);
    }

    public static <T> R<T> fail(Serializable code, String msg, T data) {
        return new R(code, msg, data);
    }

    public static <T> R<T> fail() {
        return fail(ResultCode.FAIL.getCode());
    }

    public static <T> R<T> fail(Serializable code) {
        return fail(code, ResultCode.FAIL.getDesc());
    }

    public static <T> R<T> fail(String msg) {
        return fail(ResultCode.FAIL.getCode(), msg);
    }

    public static boolean empty(R<?> r) {
        return Objects.isNull(r) || !Objects.equals(r.getCode(), ResultCode.OK.getCode()) || Objects.isNull(r.getData());
    }

    public static <T> R<T> transform(R source) {
        R<T> target = new R();
        target.setCode(source.getCode());
        target.setMsg(source.getMsg());
        return target;
    }
}
