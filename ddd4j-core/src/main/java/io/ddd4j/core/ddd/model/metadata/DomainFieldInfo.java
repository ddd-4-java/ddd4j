package io.ddd4j.core.ddd.model.metadata;

import java.lang.reflect.Field;

/**
 * Domain Model 字段元数据（仿 MyBatis-Plus {@code TableFieldInfo}）。
 *
 * <p>充血查询链路中，业务方用 Domain Model 字段引用 Lambda
 * （如 {@code User::getUserName}），通过本类可查到该字段对应的 PO 数据库列名。
 *
 * <p>字段→列名的映射来源（优先级）：
 * <ol>
 *   <li>Domain 字段有 {@code @DomainField(column = "...")} → 用注解值</li>
 *   <li>Domain 字段有 {@code @DomainField(poField = "...")} → 通过 PO 元数据查对应字段的列名</li>
 *   <li>Domain 字段名 = PO 字段名（默认约定）→ 通过 PO 元数据查列名</li>
 *   <li>fallback：{@link #poColumn} 为 null，调用方决定（驼峰转下划线）</li>
 * </ol>
 *
 * @author wandl
 * @since 2.0.x
 */
public class DomainFieldInfo {

    private final Field field;
    private final String property;
    private final String poColumn;

    public DomainFieldInfo(Field field, String poColumn) {
        this.field = field;
        this.property = field.getName();
        this.poColumn = poColumn;
    }

    public Field getField() {
        return field;
    }

    public String getProperty() {
        return property;
    }

    /**
     * 对应的 PO 数据库列名（可能为 null，表示需要 fallback）。
     */
    public String getPoColumn() {
        return poColumn;
    }
}