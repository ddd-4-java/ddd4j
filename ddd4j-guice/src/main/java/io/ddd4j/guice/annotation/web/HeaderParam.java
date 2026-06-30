package io.ddd4j.guice.annotation.web;

import java.lang.annotation.*;

/**
 * Javalin 请求头参数注解
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface HeaderParam {
    String value();

    String defaultValue() default "";
}
