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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@AllArgsConstructor
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

    /**
     * 转换为 fuinorg {@code Result<T>} 适配对象。
     * <p>需要 classpath 中有 {@code cqrs-4-java-core}（可选依赖）。
     *
     * @return fuinorg Result 适配实例
     * @see org.fuin.cqrs4j.core.Result
     */
    public org.fuin.cqrs4j.core.Result<T> toResult() {
        return new DddResultAdapter<>(this);
    }

    /**
     * 从 fuinorg {@code Result<T>} 转换为 {@code R<T>}。
     *
     * @param result fuinorg Result
     * @param <T>    数据类型
     * @return ddd4j R 实例
     */
    public static <T> R<T> fromResult(org.fuin.cqrs4j.core.Result<T> result) {
        if (result == null) {
            return fail();
        }
        Serializable code = result.getCode() != null ? result.getCode() : ResultCode.FAIL.getCode();
        String msg = result.getMessage() != null ? result.getMessage() : ResultCode.FAIL.getDesc();
        if (result.getType() == org.fuin.cqrs4j.core.ResultType.OK) {
            return ok(msg, result.getData());
        }
        return new R<>(code, msg, result.getData());
    }

    /**
     * fuinorg Result 适配器（内部类）。
     */
    private static class DddResultAdapter<T> implements org.fuin.cqrs4j.core.Result<T> {
        private final R<T> r;

        DddResultAdapter(R<T> r) {
            this.r = r;
        }

        @Override
        public org.fuin.cqrs4j.core.ResultType getType() {
            return r.isOk() ? org.fuin.cqrs4j.core.ResultType.OK : org.fuin.cqrs4j.core.ResultType.ERROR;
        }

        @Override
        public String getCode() {
            return r.getCode() != null ? r.getCode().toString() : null;
        }

        @Override
        public String getMessage() {
            return r.getMsg();
        }

        @Override
        public T getData() {
            return r.getData();
        }
    }
}