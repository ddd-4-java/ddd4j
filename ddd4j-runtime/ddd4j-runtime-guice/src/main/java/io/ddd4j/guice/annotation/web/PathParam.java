package io.ddd4j.guice.annotation.web;

import java.lang.annotation.*;

/**
 * Javalin 路径参数注解
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface PathParam {
    String value();

    String defaultValue() default "";
}
