package io.ddd4j.guice.annotation.web;

import java.lang.annotation.*;

/**
 * Javalin Cookie 参数注解
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface CookieParam {
    String value();

    String defaultValue() default "";
}
