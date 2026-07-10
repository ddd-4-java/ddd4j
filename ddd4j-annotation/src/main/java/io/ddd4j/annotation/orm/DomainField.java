package io.ddd4j.annotation.orm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Domain Model 字段到 PO 数据库列名的显式映射注解。
 *
 * <p>充血查询链路中，业务方用 Domain Model 字段引用 Lambda
 * （如 {@code User::getUserName}），基础设施层通过本注解结合 MP {@code TableInfo}
 * 翻译为 PO 列名。
 *
 * <p><b>使用场景</b>：当 Domain Model 字段名与 PO 字段名/列名不一致时使用。
 * <pre>{@code
 * public class User {
 *     @DomainField(column = "user_name")
 *     private String userName;
 * }
 * // 充血查询
 * new UserQuery().eq(User::getUserName, "alice");
 * // property="userName" → @DomainField 匹配 → "user_name"
 * }</pre>
 *
 * <p>翻译优先级链（{@code ModelHelper.getModelInfo()} 加速）：
 * <ol>
 *   <li>Domain 字段有 @DomainField → 用注解 column 值</li>
 *   <li>Domain 字段名 = PO 字段名（默认约定）→ 通过 TableInfoHelper.getTableInfo() 查 PO TableInfo</li>
 *   <li>fallback：驼峰转下划线</li>
 * </ol>
 *
 * @author wandl
 * @since 2.0.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DomainField {

    /**
     * PO 数据库列名（直接声明，绕过字段名匹配）。
     *
     * <p>与 {@link #poField()} 二选一，优先使用本字段。
     */
    String column() default "";

    /**
     * 对应的 PO 字段名（声明后通过 MP {@code TableInfoHelper} 反射 PO 字段查找列名）。
     *
     * <p>用于 Domain 字段名与 PO 字段名不一致，但 PO 字段已有 {@code @TableField} 标注的场景。
     * 例如 Domain 字段 {@code phoneNumber} 对应 PO 字段 {@code phoneNum}，PO 字段标注了
     * {@code @TableField("phone_number")}，充血查询翻译会自动通过 poField 查找列名。
     */
    String poField() default "";
}