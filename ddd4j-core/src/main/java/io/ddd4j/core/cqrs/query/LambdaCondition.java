package io.ddd4j.core.cqrs.query;

import io.ddd4j.kit.text.StrPool;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lambda 属性条件。
 */
public final class LambdaCondition implements Serializable {

    private final PropertyRef propertyRef;
    private final String operator;
    private final Object value;

    public LambdaCondition(PropertyRef propertyRef, String operator, Object value) {
        Objects.requireNonNull(propertyRef, "propertyRef must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        this.propertyRef = propertyRef;
        this.operator = operator;
        this.value = value;
    }

    public PropertyRef propertyRef() {
        return propertyRef;
    }

    public String operator() {
        return operator;
    }

    public Object value() {
        return value;
    }

    public String property() {
        return propertyRef.property();
    }

    public static LambdaCondition asc(PropertyRef property) {
        return new LambdaCondition(property, StrPool.ASC, null);
    }

    public static LambdaCondition desc(PropertyRef property) {
        return new LambdaCondition(property, StrPool.DESC, null);
    }

    public boolean isOrderBy() {
        return StrPool.ASC.equals(operator) || StrPool.DESC.equals(operator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LambdaCondition)) return false;
        LambdaCondition that = (LambdaCondition) o;
        return Objects.equals(propertyRef, that.propertyRef)
                && Objects.equals(operator, that.operator)
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(propertyRef);
        result = 31 * result + Objects.hashCode(operator);
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public String toString() {
        return "LambdaCondition{propertyRef=" + propertyRef + ", operator=" + operator
                + ", value=" + value + '}';
    }
}
