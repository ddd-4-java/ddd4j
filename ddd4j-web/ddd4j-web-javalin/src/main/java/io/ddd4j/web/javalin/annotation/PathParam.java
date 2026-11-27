package io.ddd4j.web.javalin.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * \u65b9\u6cd5\u53c2\u6570\u6ce8\u89e3\uff1a\u6309\u540d\u79f0\u7ed1\u5b9a\u8def\u5f84\uff08Path\uff09\u53d8\u91cf\u5230\u63a7\u5236\u5668\u65b9\u6cd5\u53c2\u6570\u3002
 * @since 2.0.x
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface PathParam {
    /**
     * \u8def\u5f84\u53d8\u91cf\u540d\u3002
     * @return \u8def\u5f84\u53d8\u91cf\u540d
     */
    String value();

    /**
     * \u8def\u5f84\u53d8\u91cf\u7f3a\u5931\u65f6\u4f7f\u7528\u7684\u7f3a\u7701\u503c\u3002
     * @return \u7f3a\u7701\u503c
     */
    String defaultValue() default "";
}
