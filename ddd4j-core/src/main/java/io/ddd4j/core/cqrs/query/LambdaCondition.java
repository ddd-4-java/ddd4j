package io.ddd4j.core.cqrs.query;

import java.io.Serializable;

/**
 * Lambda 查询条件记录（ORM 无关）。
 *
 * <p>存储从 {@link io.ddd4j.core.util.SFunction} 方法引用中解析出的属性名、操作符和值，
 * 由各 ORM 模块的 Repository 转换为原生查询条件。
 *
 * @param property 属性名（如 {@code "name"}、{@code "createTime"}）
 * @param operator 操作符（如 {@code "="}、{@code "LIKE"}、{@code ">"}）
 * @param value    条件值
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public record LambdaCondition(String property, String operator, Object value) implements Serializable {

    /**
     * 排序条件构造器。
     */
    public static LambdaCondition asc(String property) {
        return new LambdaCondition(property, "ASC", null);
    }

    public static LambdaCondition desc(String property) {
        return new LambdaCondition(property, "DESC", null);
    }

    /**
     * 是否为排序条件。
     */
    public boolean isOrderBy() {
        return "ASC".equals(operator) || "DESC".equals(operator);
    }
}
