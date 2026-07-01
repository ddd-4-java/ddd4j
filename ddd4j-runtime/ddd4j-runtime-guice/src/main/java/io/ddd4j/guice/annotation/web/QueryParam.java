package io.ddd4j.guice.annotation.web;

import java.lang.annotation.*;

/**
 * Javalin 查询参数注解
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface QueryParam {
    String value();

    String defaultValue() default "";
}
