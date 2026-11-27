package io.ddd4j.extension.qlexpress.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 不抛出异常的表达式执行结果。
 *
 * @param <T> 结果类型
 */

public final class QLExpressExecutionResult<T> {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u8868\u8fbe\u5f0f\u662f\u5426\u6b63\u5e38\u5b8c\u6210\u3002 */
    private final boolean success;

    /** \u8868\u8fbe\u5f0f\u6210\u529f\u6267\u884c\u540e\u8fd4\u56de\u7684\u503c\u3002 */
    private final T value;

    /** \u7a33\u5b9a\u6807\u8bc6\u6267\u884c\u5931\u8d25\u7c7b\u522b\u7684\u9519\u8bef\u7801\u3002 */
    private final String errorCode;

    /** \u8868\u8fbe\u5f0f\u6267\u884c\u5931\u8d25\u65f6\u8fd4\u56de\u7684\u8bca\u65ad\u8bf4\u660e\u3002 */
    private final String errorMessage;

    /** \u5355\u6b21\u6267\u884c\u4ece\u5f00\u59cb\u5230\u7ed3\u675f\u7684\u8017\u65f6\uff0c\u5355\u4f4d\u4e3a\u7eb3\u79d2\u3002 */
    private final long elapsedNanos;

    /**
     * \u521b\u5efa\u6210\u529f\u6267\u884c\u7ed3\u679c\u3002
     * @param <T> \u7ed3\u679c\u7c7b\u578b
     * @param value \u8868\u8fbe\u5f0f\u7ed3\u679c
     * @param elapsedNanos \u6267\u884c\u8017\u65f6\uff08\u7eb3\u79d2\uff09
     * @return \u6210\u529f\u7ed3\u679c
     */
    public static <T> QLExpressExecutionResult<T> success(T value, long elapsedNanos) {
        return new QLExpressExecutionResult<>(true, value, null, null, elapsedNanos);
    }

    /**
     * \u521b\u5efa\u5931\u8d25\u6267\u884c\u7ed3\u679c\u3002
     * @param <T> \u7ed3\u679c\u7c7b\u578b
     * @param errorCode \u9519\u8bef\u7801
     * @param errorMessage \u9519\u8bef\u6d88\u606f
     * @param elapsedNanos \u6267\u884c\u8017\u65f6\uff08\u7eb3\u79d2\uff09
     * @return \u5931\u8d25\u7ed3\u679c
     */
    public static <T> QLExpressExecutionResult<T> failure(String errorCode, String errorMessage, long elapsedNanos) {
        return new QLExpressExecutionResult<>(false, null, errorCode, errorMessage, elapsedNanos);
    }

    /**
     * 保存脚本的执行状态、返回值以及错误信息。
     *
     * @param success \u662f\u5426\u6210\u529f
     * @param value \u8868\u8fbe\u5f0f\u539f\u59cb\u7ed3\u679c\u503c
     * @param errorCode \u5f02\u5e38\u7c7b\u578b
     * @param errorMessage \u5f02\u5e38\u6d88\u606f
     * @param elapsedNanos \u6267\u884c\u8017\u65f6\uff0c\u5355\u4f4d\u7eb3\u79d2
     */

    @JsonCreator()
    public QLExpressExecutionResult(@JsonProperty("success") boolean success, @JsonProperty("value") T value, @JsonProperty("errorCode") String errorCode, @JsonProperty("errorMessage") String errorMessage, @JsonProperty("elapsedNanos") long elapsedNanos) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.elapsedNanos = elapsedNanos;
    }

    /**
     * \u8bfb\u53d6\u6210\u529f\u6807\u8bb0\u3002
     * @return \u6210\u529f\u65f6\u4e3a true
     */
    @JsonProperty("success")
    public boolean success() {
        return success;
    }

    /**
     * \u8bfb\u53d6\u8868\u8fbe\u5f0f\u7ed3\u679c\u3002
     * @return \u8868\u8fbe\u5f0f\u7ed3\u679c
     */
    @JsonProperty("value")
    public T value() {
        return value;
    }

    /**
     * \u8bfb\u53d6\u9519\u8bef\u7801\u3002
     * @return \u9519\u8bef\u7801
     */
    @JsonProperty("errorCode")
    public String errorCode() {
        return errorCode;
    }

    /**
     * \u8bfb\u53d6\u9519\u8bef\u6d88\u606f\u3002
     * @return \u9519\u8bef\u6d88\u606f
     */
    @JsonProperty("errorMessage")
    public String errorMessage() {
        return errorMessage;
    }

    /**
     * \u8bfb\u53d6\u6267\u884c\u8017\u65f6\u3002
     * @return \u6267\u884c\u8017\u65f6\uff08\u7eb3\u79d2\uff09
     */
    @JsonProperty("elapsedNanos")
    public long elapsedNanos() {
        return elapsedNanos;
    }

    /**
     * 根据<code>value</code>、<code>errorCode</code>、<code>errorMessage</code>、<code>success</code>、<code>elapsedNanos</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        QLExpressExecutionResult<?> other = (QLExpressExecutionResult<?>) obj;
        return this.success == other.success && Objects.equals(this.value, other.value) && Objects.equals(this.errorCode, other.errorCode) && Objects.equals(this.errorMessage, other.errorMessage) && this.elapsedNanos == other.elapsedNanos;
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(success);
        result = 31 * result + Objects.hashCode(value);
        result = 31 * result + Objects.hashCode(errorCode);
        result = 31 * result + Objects.hashCode(errorMessage);
        result = 31 * result + Long.hashCode(elapsedNanos);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>success</code>、<code>value</code>、<code>errorCode</code>、<code>errorMessage</code>、<code>elapsedNanos</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "QLExpressExecutionResult[success=" + success + ", value=" + value + ", errorCode=" + errorCode + ", errorMessage=" + errorMessage + ", elapsedNanos=" + elapsedNanos + "]";
    }
}
