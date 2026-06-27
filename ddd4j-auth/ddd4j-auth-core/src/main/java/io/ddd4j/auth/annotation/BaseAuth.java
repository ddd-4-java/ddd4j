package io.ddd4j.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 基础认证注解（纯 Java）
 * <p>
 * 标注在 Controller 方法上，表示该方法需要基础认证。
 * 各框架适配层通过拦截器/AOP 解析此注解。
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 3.4.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface BaseAuth {

    /** 需要的角色（任一即可） */
    String[] roles() default {};

    /** 需要的权限（任一即可） */
    String[] permissions() default {};
}
