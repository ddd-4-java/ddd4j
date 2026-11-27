package io.ddd4j.web.javalin.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * \u65b9\u6cd5\u53c2\u6570\u6ce8\u89e3\uff1a\u6309\u540d\u79f0\u7ed1\u5b9a URL \u67e5\u8be2\u4e32\uff08Query\uff09\u53c2\u6570\u5230\u63a7\u5236\u5668\u65b9\u6cd5\u53c2\u6570\u3002
 * @since 2.0.x
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface QueryParam {
    /**
     * \u67e5\u8be2\u53c2\u6570\u540d\u3002
     * @return \u67e5\u8be2\u53c2\u6570\u540d
     */
    String value();

    /**
     * \u53c2\u6570\u7f3a\u5931\u65f6\u4f7f\u7528\u7684\u7f3a\u7701\u503c\u3002
     * @return \u7f3a\u7701\u503c
     */
    String defaultValue() default "";
}
