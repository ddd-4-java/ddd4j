package io.ddd4j.guice.annotation.web;

import java.lang.annotation.*;

/**
 * Javalin Cookie 参数注解
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface CookieParam {
    String value();

    String defaultValue() default "";
}
