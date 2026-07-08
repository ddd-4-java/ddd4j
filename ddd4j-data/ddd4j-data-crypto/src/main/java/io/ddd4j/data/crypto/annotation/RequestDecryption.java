package io.ddd4j.data.crypto.annotation;

import java.lang.annotation.*;

/**
 * 请求解密注解
 * <p>标注在方法上，用于自动对请求参数进行解密处理</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RequestDecryption {
}
