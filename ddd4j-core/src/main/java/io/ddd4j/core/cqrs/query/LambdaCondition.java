package io.ddd4j.core.cqrs.query;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.InvalidObjectException;

import java.io.ObjectStreamException;

import io.ddd4j.kit.text.StrPool;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lambda 查询条件记录（ORM 无关）。
 *
 * <p>存储从 {@link io.ddd4j.core.util.SFunction} 方法引用中解析出的属性名、操作符和值，
 * 由各 ORM 模块的 Repository 转换为原生查询条件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */

public final class LambdaCondition implements Serializable {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u7c7b\u578b\u5b89\u5168\u5c5e\u6027\u5f15\u7528\u3002 */
    private final PropertyRef propertyRef;

    /** \u6761\u4ef6\u64cd\u4f5c\u7b26\u3002 */
    private final String operator;

    /** \u6761\u4ef6\u503c\u3002 */
    private final Object value;

    /**
     * 将属性引用、比较运算和值绑定为查询条件。
     *
     * @param propertyRef \u7c7b\u578b\u5b89\u5168\u5c5e\u6027\u5f15\u7528
     * @param operator \u64cd\u4f5c\u7b26\uff08\u5982 {@code "="}\u3001{@code "LIKE"}\u3001{@code ">"}\uff09
     * @param value \u6761\u4ef6\u503c
     */

    @JsonCreator()
    public LambdaCondition(@JsonProperty("propertyRef") PropertyRef propertyRef, @JsonProperty("operator") String operator, @JsonProperty("value") Object value) {
        Objects.requireNonNull(propertyRef, "propertyRef must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        this.propertyRef = propertyRef;
        this.operator = operator;
        this.value = value;
    }

    /**
     * \u83b7\u53d6\u5c5e\u6027\u540d\u3002
     * @return Java \u5c5e\u6027\u540d
     */
    public String property() {
        return propertyRef.property();
    }

    /**
     * \u6392\u5e8f\u6761\u4ef6\u6784\u9020\u5668\u3002
     *
     * @param property \u5f85\u6392\u5e8f\u5c5e\u6027
     * @return \u5347\u5e8f\u6392\u5e8f\u6761\u4ef6
     */
    public static LambdaCondition asc(PropertyRef property) {
        return new LambdaCondition(property, StrPool.ASC, null);
    }

    /**
     * \u521b\u5efa\u964d\u5e8f\u6392\u5e8f\u6761\u4ef6\u3002
     * @param property \u5f85\u6392\u5e8f\u5c5e\u6027
     * @return \u964d\u5e8f\u6392\u5e8f\u6761\u4ef6
     */
    public static LambdaCondition desc(PropertyRef property) {
        return new LambdaCondition(property, StrPool.DESC, null);
    }

    /**
     * \u662f\u5426\u4e3a\u6392\u5e8f\u6761\u4ef6\u3002
     *
     * @return \u64cd\u4f5c\u7b26\u4e3a\u5347\u5e8f\u6216\u964d\u5e8f\u65f6\u4e3a true
     */
    public boolean isOrderBy() {
        return StrPool.ASC.equals(operator) || StrPool.DESC.equals(operator);
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u5c5e\u6027\u5f15\u7528\u3002
     * @return \u5c5e\u6027\u5f15\u7528
     */
    @JsonProperty("propertyRef")
    public PropertyRef propertyRef() {
        return propertyRef;
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u6761\u4ef6\u64cd\u4f5c\u7b26\u3002
     * @return \u6761\u4ef6\u64cd\u4f5c\u7b26
     */
    @JsonProperty("operator")
    public String operator() {
        return operator;
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u6761\u4ef6\u503c\u3002
     * @return \u6761\u4ef6\u503c
     */
    @JsonProperty("value")
    public Object value() {
        return value;
    }

    /**
     * \u53cd\u5e8f\u5217\u5316\u65f6\u91cd\u65b0\u6821\u9a8c\u5c5e\u6027\u5f15\u7528\u548c\u64cd\u4f5c\u7b26\u3002
     * @return \u6821\u9a8c\u540e\u7684\u67e5\u8be2\u6761\u4ef6
     * @throws ObjectStreamException \u67e5\u8be2\u6761\u4ef6\u65e0\u6548\u65f6
     */
    private Object readResolve() throws ObjectStreamException {
        try {
            return new LambdaCondition(propertyRef, operator, value);
        } catch (RuntimeException cause) {
            InvalidObjectException failure = new InvalidObjectException(cause.getMessage());
            failure.initCause(cause);
            throw failure;
        }
    }

    /**
     * 根据<code>propertyRef</code>、<code>operator</code>、<code>value</code>比较对象的值是否相等。
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
        LambdaCondition other = (LambdaCondition) obj;
        return Objects.equals(this.propertyRef, other.propertyRef) && Objects.equals(this.operator, other.operator) && Objects.equals(this.value, other.value);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(propertyRef);
        result = 31 * result + Objects.hashCode(operator);
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>propertyRef</code>、<code>operator</code>、<code>value</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "LambdaCondition[propertyRef=" + propertyRef + ", operator=" + operator + ", value=" + value + "]";
    }
}
