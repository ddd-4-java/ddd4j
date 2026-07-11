package io.ddd4j.core.cqrs.query;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lambda 查询条件记录（ORM 无关）。
 *
 * <p>存储从 {@link io.ddd4j.core.util.SFunction} 方法引用中解析出的属性名、操作符和值，
 * 由各 ORM 模块的 Repository 转换为原生查询条件。
 *
 * @param propertyRef 类型安全属性引用
 * @param operator    操作符（如 {@code "="}、{@code "LIKE"}、{@code ">"}）
 * @param value       条件值
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public record LambdaCondition(PropertyRef propertyRef, String operator, Object value) implements Serializable {

    public LambdaCondition {
        Objects.requireNonNull(propertyRef, "propertyRef must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
    }

    public String property() {
        return propertyRef.property();
    }

    /**
     * 排序条件构造器。
     */
    public static LambdaCondition asc(PropertyRef property) {
        return new LambdaCondition(property, "ASC", null);
    }

    public static LambdaCondition desc(PropertyRef property) {
        return new LambdaCondition(property, "DESC", null);
    }

    /**
     * 是否为排序条件。
     */
    public boolean isOrderBy() {
        return "ASC".equals(operator) || "DESC".equals(operator);
    }
}
