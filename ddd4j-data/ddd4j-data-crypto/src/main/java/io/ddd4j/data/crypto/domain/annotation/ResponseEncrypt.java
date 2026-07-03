package io.ddd4j.data.crypto.domain.annotation;

import java.lang.annotation.*;

/**
 * 响应加密注解
 * <p>标注在方法上，用于自动对响应结果进行加密处理</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ResponseEncrypt {
}
