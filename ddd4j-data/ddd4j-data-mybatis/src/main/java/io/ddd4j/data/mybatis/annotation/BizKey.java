package io.ddd4j.data.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务key值参数注解
 * 用于标记PO中的业务key值
 *
 * @author Loong Wan
 * @version 1.0.0
 * @date 2022年2月12日
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface BizKey {

}
