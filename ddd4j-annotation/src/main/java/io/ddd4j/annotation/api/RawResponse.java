package io.ddd4j.annotation.api;

import io.ddd4j.annotation.Contract;

import java.lang.annotation.*;

/**
 * 原生响应，注解了的Controller方法将不受BaseRestControllerAdvice控制，即不会在外自动包装R对象
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Contract
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface RawResponse {
}
