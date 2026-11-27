package io.ddd4j.core.api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * \u7edf\u4e00\u63a5\u53e3\u54cd\u5e94\uff0c\u6807\u51c6\u7684\u54cd\u5e94\u6570\u636e\u7ed3\u6784
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @param <T> \u7531 R \u5904\u7406\u7684\u6cdb\u578b\u7c7b\u578b
 */
@Data
@AllArgsConstructor
public class R<T> implements IR {

    // 编码：0/200、请求成功；500、请求成功但服务异常；403、未登录或者token已失效；401、已登录没有权限。
    /** \u7f16\u7801\uff1a0/200\u3001\u8bf7\u6c42\u6210\u529f\uff1b500\u3001\u8bf7\u6c42\u6210\u529f\u4f46\u670d\u52a1\u5f02\u5e38\uff1b403\u3001\u672a\u767b\u5f55\u6216\u8005token\u5df2\u5931\u6548\uff1b401\u3001\u5df2\u767b\u5f55\u6ca1\u6709\u6743\u9650\u3002 */
    protected Serializable code;
    // 返回信息
    /** \u8fd4\u56de\u4fe1\u606f */
    protected String msg;
    // 响应数据
    /** \u54cd\u5e94\u6570\u636e */
    protected T data;

    /** \u521b\u5efa\u54cd\u5e94\u5bf9\u8c61\u3002 */
    public R() {
        this(ResultCode.OK.getCode(), ResultCode.OK.getDesc());
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
        this(ResultCode.OK.getCode(), ResultCode.OK.getDesc(), data);
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=0\uff09\uff0c\u4e0d\u643a\u5e26\u6570\u636e\u3002
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> ok() {
        return new R<>();
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=0\uff09\uff0c\u643a\u5e26\u6570\u636e\u3002
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param payload \u54cd\u5e94\u6570\u636e
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> ok(T payload) {
        return new R<>(payload);
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f\uff08code=0\uff09\uff0c\u643a\u5e26\u81ea\u5b9a\u4e49\u6d88\u606f\u4e0e\u6570\u636e\u3002
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param msg \u54cd\u5e94\u6d88\u606f
     * @param data \u54cd\u5e94\u6570\u636e
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> ok(String msg, T data) {
        return new R(ResultCode.OK.getCode(), msg, data);
    }

    /**
     * 按 <code>fail</code> 的契约计算并返回结果。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param msg 传入此操作的 <code>String</code> 类型 <code>msg</code>
     * @return 当前操作得到的 <code>R&lt;T&gt;</code> 结果
     */
    public static <T> R<T> fail(Serializable code, String msg) {
        return new R(code, msg);
    }

    /**
     * 按 <code>fail</code> 的契约计算并返回结果。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param msg 传入此操作的 <code>String</code> 类型 <code>msg</code>
     * @param data 随响应携带的业务数据
     * @return 当前操作得到的 <code>R&lt;T&gt;</code> 结果
     */
    public static <T> R<T> fail(Serializable code, String msg, T data) {
        return new R(code, msg, data);
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u5931\u8d25\uff08code=400\uff09\uff0c\u4e0d\u643a\u5e26\u6570\u636e\u3002
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> fail() {
        return fail(ResultCode.FAIL.getCode());
    }

    /**
     * \u8bf7\u6c42/\u64cd\u4f5c\u5931\u8d25\uff0c\u4f7f\u7528\u6307\u5b9a\u54cd\u5e94\u7801\u548c\u9ed8\u8ba4\u5931\u8d25\u6d88\u606f\u3002
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @param code \u54cd\u5e94\u7801
     * @return \u54cd\u5e94\u5bf9\u8c61
     */
    public static <T> R<T> fail(Serializable code) {
        return fail(code, ResultCode.FAIL.getDesc());
    }

    /**
     * 执行 <code>fail</code>，调用 <code>FAIL.getCode</code>。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param msg 传入此操作的 <code>String</code> 类型 <code>msg</code>
     * @return 返回表达式 <code>fail(ResultCode.FAIL.getCode(), msg)</code>
     */
    public static <T> R<T> fail(String msg) {
        return fail(ResultCode.FAIL.getCode(), msg);
    }

    // === cloud 兼容别名（failed = fail，isOk 语义对齐 cloud SUCCESS=0） ===

    /**
     * \u5931\u8d25\u54cd\u5e94\uff08cloud \u517c\u5bb9\u522b\u540d\uff0c\u7b49\u4ef7\u4e8e {@link #fail()}\uff09\u3002
     *
     * @param <T> 当前操作使用的泛型类型
     * @return 返回表达式 <code>fail()</code>
     */
    public static <T> R<T> failed() {
        return fail();
    }

    /**
     * \u5931\u8d25\u54cd\u5e94\uff08cloud \u517c\u5bb9\u522b\u540d\uff0c\u7b49\u4ef7\u4e8e {@link #fail(String)}\uff09\u3002
     *
     * @param <T> 当前操作使用的泛型类型
     * @param msg 传入此操作的 <code>String</code> 类型 <code>msg</code>
     * @return 返回表达式 <code>fail(msg)</code>
     */
    public static <T> R<T> failed(String msg) {
        return fail(msg);
    }

    /**
     * \u5931\u8d25\u54cd\u5e94\uff08cloud \u517c\u5bb9\u522b\u540d\uff0c\u7b49\u4ef7\u4e8e {@link #fail(T)}\uff09\u3002
     *
     * @param <T> 当前操作使用的泛型类型
     * @param data 随响应携带的业务数据
     * @return 当前操作得到的 <code>R&lt;T&gt;</code> 结果
     */
    public static <T> R<T> failed(T data) {
        return fail(Objects.nonNull(data) ? ResultCode.FAIL.getCode() : ResultCode.FAIL.getCode(), ResultCode.FAIL.getDesc());
    }

    /**
     * \u5931\u8d25\u54cd\u5e94\uff08cloud \u517c\u5bb9\u522b\u540d\uff0c\u7b49\u4ef7\u4e8e {@link #fail(Serializable, String)}\uff09\u3002
     *
     * @param <T> 当前操作使用的泛型类型
     * @param code 随响应返回的业务状态编码
     * @param msg 传入此操作的 <code>String</code> 类型 <code>msg</code>
     * @return 返回表达式 <code>fail(code, msg)</code>
     */
    public static <T> R<T> failed(Serializable code, String msg) {
        return fail(code, msg);
    }

    /**
     * \u5931\u8d25\u54cd\u5e94\uff08cloud \u517c\u5bb9\u522b\u540d\uff0c\u7b49\u4ef7\u4e8e {@link #fail(Serializable, String, T)}\uff09\u3002
     *
     * @param <T> 当前操作使用的泛型类型
     * @param data 随响应携带的业务数据
     * @param msg 传入此操作的 <code>String</code> 类型 <code>msg</code>
     * @return 返回表达式 <code>fail(ResultCode.FAIL.getCode(), msg, data)</code>
     */
    public static <T> R<T> failed(T data, String msg) {
        return fail(ResultCode.FAIL.getCode(), msg, data);
    }

    /**
     * \u5931\u8d25\u54cd\u5e94\uff08cloud \u517c\u5bb9\u522b\u540d\uff0c\u5e26 data + code + msg\uff09\u3002
     *
     * @param <T> 当前操作使用的泛型类型
     * @param data 随响应携带的业务数据
     * @param code 随响应返回的业务状态编码
     * @param msg 传入此操作的 <code>String</code> 类型 <code>msg</code>
     * @return 返回表达式 <code>fail(code, msg, data)</code>
     */
    public static <T> R<T> failed(T data, Serializable code, String msg) {
        return fail(code, msg, data);
    }

    /**
     * \u68c0\u67e5\u54cd\u5e94\u662f\u5426\u4e3a\u7a7a\u3002
     * @param r \u5f85\u68c0\u67e5\u54cd\u5e94
     * @return \u54cd\u5e94\u7f3a\u5931\u3001\u5931\u8d25\u6216\u6570\u636e\u4e3a\u7a7a\u65f6\u4e3a true
     */
    public static boolean empty(R<?> r) {
        return Objects.isNull(r) || !Objects.equals(r.getCode(), ResultCode.OK.getCode()) || Objects.isNull(r.getData());
    }

    /**
     * 读取<code>target</code> 的当前值。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param source \u5f85\u8f6c\u6362\u54cd\u5e94
     * @return 返回表达式 <code>target</code>
     */
    public static <T> R<T> transform(R source) {
        R<T> target = new R();
        target.setCode(source.getCode());
        target.setMsg(source.getMsg());
        return target;
    }

    /**
     * \u5224\u65ad\u54cd\u5e94\u7801\u662f\u5426\u8868\u793a\u6210\u529f\u3002
     * @return \u54cd\u5e94\u7801\u8868\u793a\u6210\u529f\u65f6\u4e3a true
     */
    public Boolean isOk() {
        return Objects.equals(this.getCode(), ResultCode.OK.getCode()) || Objects.equals(this.getCode(), ResultCode.SUCCESS.getCode());
    }

    /**
     * \u5224\u65ad\u54cd\u5e94\u662f\u5426\u5931\u8d25\u6216\u65e0\u6570\u636e\u3002
     * @return \u54cd\u5e94\u5931\u8d25\u6216\u6570\u636e\u4e3a\u7a7a\u65f6\u4e3a true
     */
    public Boolean isEmpty() {
        return !isOk() || Objects.isNull(data);
    }

}
