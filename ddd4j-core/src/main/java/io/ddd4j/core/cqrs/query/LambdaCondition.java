package io.ddd4j.core.cqrs.query;

import java.io.Serializable;
import java.util.Objects;

/** ORM 无关的 Lambda 查询或排序条件。 */
public final class LambdaCondition implements Serializable {
    private static final long serialVersionUID = 1L;
    private final PropertyRef propertyRef; private final String operator; private final Object value;
    public LambdaCondition(PropertyRef propertyRef, String operator, Object value) { this.propertyRef = Objects.requireNonNull(propertyRef, "propertyRef"); this.operator = Objects.requireNonNull(operator, "operator"); this.value = value; }
    public PropertyRef getPropertyRef() { return propertyRef; } public String getOperator() { return operator; } public Object getValue() { return value; }
    public String property() { return propertyRef.getProperty(); }
    public static LambdaCondition asc(PropertyRef property) { return new LambdaCondition(property, "ASC", null); }
    public static LambdaCondition desc(PropertyRef property) { return new LambdaCondition(property, "DESC", null); }
    public boolean isOrderBy() { return "ASC".equals(operator) || "DESC".equals(operator); }
}
