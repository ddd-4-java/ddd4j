package io.ddd4j.boot.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 系统ID注解
 * 用于标记PO中的系统ID字段
 *
 * @author Jensen
 * @version 1.0.0
 * @公众号 架构师修行录
 * @date 2022年2月12日
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface SystemId {

}
