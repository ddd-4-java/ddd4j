package io.ddd4j.web.auth.annotation;

import java.lang.annotation.*;

/**
 * 针对接口开放给第三方应用调用的接口注解;
 * 请求头必须带 Authorization 字段
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BaseAuth {
}
