package io.ddd4j.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 默认排序注解
 * 用于标记PO中的默认排序字段
 *
 * @author Loong Wan
 * @version 1.0.0
 * @date 2022年2月12日
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface OrderBy {
    String[] value() default "";
}
