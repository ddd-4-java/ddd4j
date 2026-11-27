package io.ddd4j.annotation.api;

import io.ddd4j.annotation.Contract;

import java.lang.annotation.*;

/**
 * Api模块注解
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Contract
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface ApiModule {

    /**
     * \u64cd\u4f5c\u6a21\u5757
     *
     * @return \u64cd\u4f5c\u6a21\u5757
     */
    String module() default "";

    /**
     * \u4e1a\u52a1\u540d\u79f0
     *
     * @return \u4e1a\u52a1\u540d\u79f0
     */
    String business() default "";

}
