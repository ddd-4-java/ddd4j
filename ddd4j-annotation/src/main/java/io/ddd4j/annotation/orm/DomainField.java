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
     * PO \u6570\u636e\u5e93\u5217\u540d\uff08\u76f4\u63a5\u58f0\u660e\uff0c\u7ed5\u8fc7\u5b57\u6bb5\u540d\u5339\u914d\uff09\u3002
     *
     *  <p>\u4e0e {@link #poField()} \u4e8c\u9009\u4e00\uff0c\u4f18\u5148\u4f7f\u7528\u672c\u5b57\u6bb5\u3002
     *
     * @return PO \u6570\u636e\u5e93\u5217\u540d
     */
    String column() default "";

    /**
     * \u5bf9\u5e94\u7684 PO \u5b57\u6bb5\u540d\uff08\u58f0\u660e\u540e\u901a\u8fc7 MP {@code TableInfoHelper} \u53cd\u5c04 PO \u5b57\u6bb5\u67e5\u627e\u5217\u540d\uff09\u3002
     *
     *  <p>\u7528\u4e8e Domain \u5b57\u6bb5\u540d\u4e0e PO \u5b57\u6bb5\u540d\u4e0d\u4e00\u81f4\uff0c\u4f46 PO \u5b57\u6bb5\u5df2\u6709 {@code @TableField} \u6807\u6ce8\u7684\u573a\u666f\u3002
     *  \u4f8b\u5982 Domain \u5b57\u6bb5 {@code phoneNumber} \u5bf9\u5e94 PO \u5b57\u6bb5 {@code phoneNum}\uff0cPO \u5b57\u6bb5\u6807\u6ce8\u4e86
     *  {@code @TableField("phone_number")}\uff0c\u5145\u8840\u67e5\u8be2\u7ffb\u8bd1\u4f1a\u81ea\u52a8\u901a\u8fc7 poField \u67e5\u627e\u5217\u540d\u3002
     *
     * @return \u5bf9\u5e94\u7684 PO \u5b57\u6bb5\u540d
     */
    String poField() default "";
}