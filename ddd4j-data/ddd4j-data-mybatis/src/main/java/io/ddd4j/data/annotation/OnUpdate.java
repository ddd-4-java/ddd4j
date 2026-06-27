package io.ddd4j.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自动更新注解
 * 用于标记PO中的自动更新字段，如果字段类型是LocalDateTime则设置为LocalDateTime.now()，如果字段类型是LocalDate则设置为LocalDate.now()
 *
 * @author Jensen
 * @version 1.0.0
 * @公众号 架构师修行录
 * @date 2022年2月12日
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface OnUpdate {

}
