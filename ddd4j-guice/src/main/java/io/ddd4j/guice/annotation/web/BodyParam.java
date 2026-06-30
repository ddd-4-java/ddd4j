package io.ddd4j.guice.annotation.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Javalin 请求体参数注解
 *
 * <p>Javalin 6 没有原生请求体参数注解（用 {@code ctx.body()} 取），本注解由 ddd4j-javalin
 * 反射注入框架实现。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface BodyParam {
    String value() default "";

    boolean required() default true;
}
