package io.ddd4j.sample.micronaut.satoken;

import io.micronaut.aop.Around;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Keeps the native subject on the thread executing the synchronous HTTP route. */
@Around
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NativeAuthenticated {
}
