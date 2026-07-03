package io.ddd4j.guice.annotation.web;

import java.lang.annotation.*;

/**
 * Javalin Context 注入注解
 *
 * <p>Javalin 必须手动传递 Context，本注解由 ddd4j-javalin 反射注入框架实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Context {
}
