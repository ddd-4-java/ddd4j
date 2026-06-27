package io.ddd4j.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用基础认证注解（纯 Java）
 * <p>
 * 标注在配置类上，启用基础认证功能。
 * 各框架适配层扫描此注解并注册拦截器。
 *
 * @author wandl
 * @since 3.4.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnableBaseAuth {

    /** 拦截路径模式 */
    String[] pathPatterns() default {"/**"};

    /** 排除路径模式 */
    String[] excludePatterns() default {};
}
