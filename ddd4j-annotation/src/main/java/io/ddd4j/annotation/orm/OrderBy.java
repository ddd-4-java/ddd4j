package io.ddd4j.annotation.orm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 默认排序注解
 * 用于标记PO中的默认排序字段
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0.0
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface OrderBy {
    String[] value() default "";
}
