package io.ddd4j.annotation.api;

import java.lang.annotation.*;

/**
 * 原生响应，注解了的Controller方法将不受BaseRestControllerAdvice控制，即不会在外自动包装R对象
 *
 * @author Loong Wan
 */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface RawResponse {
}
