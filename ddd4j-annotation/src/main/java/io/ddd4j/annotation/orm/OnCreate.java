package io.ddd4j.annotation.orm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自动创建注解
 * 用于标记PO中的自动创建字段，如果字段类型是LocalDateTime则设置为LocalDateTime.now()，如果字段类型是LocalDate则设置为LocalDate.now()
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0.0
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface OnCreate {

}
