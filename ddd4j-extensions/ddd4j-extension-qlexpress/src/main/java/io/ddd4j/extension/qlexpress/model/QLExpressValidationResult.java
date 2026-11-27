package io.ddd4j.extension.qlexpress.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 表达式语法校验结果。
 */
public final class QLExpressValidationResult {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u8868\u8fbe\u5f0f\u662f\u5426\u901a\u8fc7\u8bed\u6cd5\u9a8c\u8bc1\u3002 */
    private final boolean valid;

    /** \u9a8c\u8bc1\u5931\u8d25\u65f6\u8fd4\u56de\u7ed9\u8c03\u7528\u65b9\u7684\u9519\u8bef\u8bf4\u660e\u3002 */
    private final String message;

    /**
     * \u521b\u5efa\u8bed\u6cd5\u6821\u9a8c\u6210\u529f\u7ed3\u679c\u3002
     * @return \u6210\u529f\u7ed3\u679c
     */
    public static QLExpressValidationResult success() {
        return new QLExpressValidationResult(true, "表达式语法正确");
    }

    /**
     * \u521b\u5efa\u8bed\u6cd5\u6821\u9a8c\u5931\u8d25\u7ed3\u679c\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     * @return \u5931\u8d25\u7ed3\u679c
     */
    public static QLExpressValidationResult invalid(String message) {
        return new QLExpressValidationResult(false, message);
    }

    /**
     * \u521b\u5efa\u8bed\u6cd5\u6821\u9a8c\u7ed3\u679c\u3002
     * @param valid \u662f\u5426\u6709\u6548
     * @param message \u6821\u9a8c\u6d88\u606f
     */
    @JsonCreator()
    public QLExpressValidationResult(@JsonProperty("valid") boolean valid, @JsonProperty("message") String message) {
        this.valid = valid;
        this.message = message;
    }

    /**
     * \u8bfb\u53d6\u6821\u9a8c\u72b6\u6001\u3002
     * @return \u6709\u6548\u65f6\u4e3a true
     */
    @JsonProperty("valid")
    public boolean valid() {
        return valid;
    }

    /**
     * \u8bfb\u53d6\u6821\u9a8c\u6d88\u606f\u3002
     * @return \u6821\u9a8c\u6d88\u606f
     */
    @JsonProperty("message")
    public String message() {
        return message;
    }

    /**
     * 根据<code>message</code>、<code>valid</code>比较对象的值是否相等。
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
        QLExpressValidationResult other = (QLExpressValidationResult) obj;
        return this.valid == other.valid && Objects.equals(this.message, other.message);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(valid);
        result = 31 * result + Objects.hashCode(message);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>valid</code>、<code>message</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "QLExpressValidationResult[valid=" + valid + ", message=" + message + "]";
    }
}
